package com.facedb.service;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import com.facedb.dto.PersonResponse;
import com.facedb.model.Person;
import com.facedb.repository.PersonRepository;

@Service
public class PersonService {

    private final PersonRepository personRepository;
    private final FileStorageService fileStorageService;

    public PersonService(PersonRepository personRepository, FileStorageService fileStorageService) {
        this.personRepository = personRepository;
        this.fileStorageService = fileStorageService;
    }

    public List<Person> findAll() {
        return personRepository.findAll();
    }

    public List<Person> search(String query) {
        if (query == null || query.isBlank()) return findAll();
        String normalizedQuery = query.trim();
        // Numeric query -> treat as a database ID lookup as well as a field search.
        List<Person> fieldMatches = personRepository
                .findByFullNameContainingIgnoreCaseOrCriminalIdContainingIgnoreCaseOrFirNumberContainingIgnoreCase(
                        normalizedQuery, normalizedQuery, normalizedQuery);
        if (normalizedQuery.matches("\\d+")) {
            try {
                Long databaseId = Long.valueOf(normalizedQuery);
                return personRepository.findById(databaseId)
                        .<List<Person>>map(person -> {
                            java.util.LinkedHashMap<Long, Person> results = new java.util.LinkedHashMap<>();
                            results.put(person.getId(), person);
                            fieldMatches.forEach(match -> results.putIfAbsent(match.getId(), match));
                            return new java.util.ArrayList<>(results.values());
                        })
                        .orElse(fieldMatches);
            } catch (NumberFormatException e) {
                return fieldMatches;
            }
        }
        return fieldMatches;
    }

    public Person findById(Long id) {
        return personRepository.findById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("Person not found: " + id));
    }

    public Person save(Person person) {
        return personRepository.save(person);
    }

    public Person delete(Long id) {
        Person person = findById(id);
        personRepository.delete(person);
        return person;
    }

    public int convertExistingPhotosToWebP() {
        List<Person> people = personRepository.findAll();
        Path uploadsDir = fileStorageService.getUploadDirectory();
        int convertedCount = 0;

        for (Person person : people) {
            String photoPath = person.getPhotoPath();
            if (photoPath == null || photoPath.toLowerCase().endsWith(".webp")) {
                continue;
            }

            Path sourcePath;
            try {
                sourcePath = fileStorageService.resolveStoredPath(photoPath);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (!Files.exists(sourcePath)) {
                continue;
            }

            try {
                BufferedImage image = ImageIO.read(sourcePath.toFile());
                if (image == null) {
                    continue;
                }

                Files.createDirectories(uploadsDir);
                Path targetPath = uploadsDir.resolve(UUID.randomUUID() + ".webp").normalize();
                if (!targetPath.startsWith(uploadsDir)) {
                    throw new IllegalArgumentException("Invalid target upload path");
                }

                if (!ImageIO.write(image, "webp", targetPath.toFile())) {
                    throw new RuntimeException("Failed to write WebP file for " + sourcePath);
                }

                Files.deleteIfExists(sourcePath);
                person.setPhotoPath(targetPath.toString());
                personRepository.save(person);
                convertedCount++;
            } catch (java.io.IOException | RuntimeException ignored) {
                // Skip files we cannot convert and continue with the rest.
            }
        }

        return convertedCount;
    }

    public String extractFaceEmbedding(String photoPath) {
        if (photoPath == null) return null;
        try {
            String absolutePath = new java.io.File(photoPath).getAbsolutePath();
            String scriptPath = "scripts/extract_embedding.py";
            if (!Files.exists(Paths.get(scriptPath))) {
                scriptPath = "backend/scripts/extract_embedding.py";
            }
            
            // Use the Python 3 executable installed by the deployment image.
            // Many Linux distributions no longer provide a `python` alias.
            ProcessBuilder pb = new ProcessBuilder("python3", scriptPath, absolutePath);
            pb.environment().put("OPENCV_LOG_LEVEL", "ERROR");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream())
            );
            
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            String combinedOutput = output.toString().trim();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                if (combinedOutput.contains("Error:")) {
                    int errIdx = combinedOutput.indexOf("Error:");
                    String errLine = combinedOutput.substring(errIdx).split("\n")[0];
                    throw new IllegalArgumentException(errLine.replace("Error:", "").trim());
                }
                throw new RuntimeException("Python face embedding extraction failed: " + combinedOutput);
            }
            String[] outputLines = combinedOutput.split("\\R");
            String embedding = outputLines[outputLines.length - 1].trim();
            String[] values = embedding.split(",");
            if (values.length != 512) {
                throw new RuntimeException("Face embedding service returned an invalid vector");
            }
            for (String value : values) {
                if (!Double.isFinite(Double.parseDouble(value))) {
                    throw new RuntimeException("Face embedding service returned an invalid vector");
                }
            }
            return embedding;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Face recognition service was interrupted", e);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to run face recognition service", e);
        }
    }

    public double calculateCosineSimilarity(String emb1, String emb2) {
        if (emb1 == null || emb2 == null) return 0.0;
        try {
            String[] tokens1 = emb1.split(",");
            String[] tokens2 = emb2.split(",");
            if (tokens1.length != tokens2.length || tokens1.length == 0) return 0.0;
            
            double dotProduct = 0.0;
            double normA = 0.0;
            double normB = 0.0;
            
            for (int i = 0; i < tokens1.length; i++) {
                double valA = Double.parseDouble(tokens1[i]);
                double valB = Double.parseDouble(tokens2[i]);
                if (!Double.isFinite(valA) || !Double.isFinite(valB)) return 0.0;
                dotProduct += valA * valB;
                normA += valA * valA;
                normB += valB * valB;
            }
            
            if (normA == 0.0 || normB == 0.0) return 0.0;
            return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public double mapSimilarityToScore(double sim) {
        if (sim <= 0.0) return 0.0;
        double score;
        if (sim < 0.20) {
            score = sim * 250.0;
        } else if (sim < 0.30) {
            score = 50.0 + (sim - 0.20) * 250.0;
        } else if (sim < 0.40) {
            score = 75.0 + (sim - 0.30) * 150.0;
        } else if (sim < 0.50) {
            score = 90.0 + (sim - 0.40) * 50.0;
        } else {
            score = 95.0 + (sim - 0.50) * 10.0;
            if (score > 100.0) score = 100.0;
        }
        return Math.round(score * 100.0) / 100.0;
    }

    public List<PersonResponse> findTopMatches(String queryEmbedding, int limit) {
        return personRepository.findAll().stream()
                .filter(p -> p.getFaceEmbedding() != null)
                .map(p -> {
                    PersonResponse resp = PersonResponse.from(p);
                    double similarity = calculateCosineSimilarity(queryEmbedding, p.getFaceEmbedding());
                    resp.matchScore = mapSimilarityToScore(similarity);
                    return resp;
                })
                .filter(resp -> resp.matchScore >= 75.0)
                .sorted((a, b) -> Double.compare(b.matchScore, a.matchScore))
                .limit(limit)
                .toList();
    }
}
