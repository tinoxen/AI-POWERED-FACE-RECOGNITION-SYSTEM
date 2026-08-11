package com.facedb.controller;

import com.facedb.dto.PersonResponse;
import com.facedb.model.Person;
import com.facedb.service.AuditService;
import com.facedb.service.FileStorageService;
import com.facedb.service.PersonService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/persons")
public class PersonController {

    private static final Logger log = LoggerFactory.getLogger(PersonController.class);

    private final PersonService personService;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;

    public PersonController(PersonService personService, FileStorageService fileStorageService,
                             AuditService auditService) {
        this.personService = personService;
        this.fileStorageService = fileStorageService;
        this.auditService = auditService;
    }

    @GetMapping
    public List<PersonResponse> list(@RequestParam(required = false) String q,
                                      Authentication auth, HttpServletRequest req) {
        List<Person> results = personService.search(q);
        auditService.log(auth.getName(), "SEARCH_PERSONS", null, "query=" + q, req.getRemoteAddr());
        return results.stream().map(PersonResponse::from).toList();
    }

    @GetMapping("/{id}")
    public PersonResponse get(@PathVariable Long id, Authentication auth, HttpServletRequest req) {
        Person p = personService.findById(id);
        auditService.log(auth.getName(), "VIEW_PERSON", id, null, req.getRemoteAddr());
        return PersonResponse.from(p);
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<FileSystemResource> photo(@PathVariable Long id) {
        Person p = personService.findById(id);
        if (p.getPhotoPath() == null) return ResponseEntity.notFound().build();
        
        MediaType mediaType = MediaType.IMAGE_JPEG;
        String path = p.getPhotoPath().toLowerCase();
        if (path.endsWith(".png")) {
            mediaType = MediaType.IMAGE_PNG;
        } else if (path.endsWith(".webp")) {
            mediaType = MediaType.parseMediaType("image/webp");
        } else if (path.endsWith(".gif")) {
            mediaType = MediaType.IMAGE_GIF;
        }
        
        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(new FileSystemResource(p.getPhotoPath()));
    }

    @PostMapping(value = "/match", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<PersonResponse> match(@RequestParam MultipartFile photo,
                                      Authentication auth, HttpServletRequest req) throws Exception {
        validateImageUpload(photo);
        
        String path = fileStorageService.store(photo);
        try {
            String queryEmbedding = personService.extractFaceEmbedding(path);
            auditService.log(auth.getName(), "MATCH_FACE", null, "photo=" + photo.getOriginalFilename(), req.getRemoteAddr());
            return personService.findTopMatches(queryEmbedding, 5);
        } finally {
            try {
                java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(path));
            } catch (Exception ignored) {}
        }
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PersonResponse create(@RequestParam String fullName,
                                  @RequestParam(required = false) LocalDate dateOfBirth,
                                  @RequestParam(required = false) String address,
                                  @RequestParam(required = false) String otherDetails,
                                  @RequestParam(required = false) String criminalId,
                                  @RequestParam(required = false) String gender,
                                  @RequestParam(required = false) String phoneNumber,
                                  @RequestParam(required = false) String crimeCategory,
                                  @RequestParam(required = false) String crimeDescription,
                                  @RequestParam(required = false) String firNumber,
                                  @RequestParam(required = false) LocalDate arrestDate,
                                  @RequestParam(required = false) String policeStation,
                                  @RequestParam(required = false) String currentStatus,
                                  @RequestParam(required = false) MultipartFile photo,
                                  Authentication auth, HttpServletRequest req) throws Exception {
        if (photo == null || photo.isEmpty()) {
            throw new IllegalArgumentException("No mugshot photo provided. Please upload a clear front-facing face image.");
        }
        validateImageUpload(photo);
        Person p = new Person();
        p.setFullName(fullName);
        p.setDateOfBirth(dateOfBirth);
        p.setAddress(address);
        p.setOtherDetails(otherDetails);
        p.setCriminalId(criminalId);
        p.setGender(gender);
        p.setPhoneNumber(phoneNumber);
        p.setCrimeCategory(crimeCategory);
        p.setCrimeDescription(crimeDescription);
        p.setFirNumber(firNumber);
        p.setArrestDate(arrestDate);
        p.setPoliceStation(policeStation);
        p.setCurrentStatus(currentStatus);
        p.setCreatedBy(auth.getName());

        if (photo != null && !photo.isEmpty()) {
            String path = fileStorageService.store(photo);
            p.setPhotoPath(path);
            try {
                p.setFaceEmbedding(personService.extractFaceEmbedding(path));
            } catch (RuntimeException e) {
                // A record can still be registered even when the uploaded
                // image cannot produce a usable biometric template. It will
                // be excluded from face-match results until its photo is
                // replaced with a suitable image.
                log.warn("Created record without a face embedding for {}: {}",
                        fullName, e.getMessage());
            }
        }

        Person saved = personService.save(p);
        auditService.log(auth.getName(), "CREATE_PERSON", saved.getId(), null, req.getRemoteAddr());
        return PersonResponse.from(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PersonResponse update(@PathVariable Long id, @RequestBody Person updates,
                                  Authentication auth, HttpServletRequest req) {
        Person existing = personService.findById(id);
        existing.setFullName(updates.getFullName());
        existing.setDateOfBirth(updates.getDateOfBirth());
        existing.setAddress(updates.getAddress());
        existing.setOtherDetails(updates.getOtherDetails());
        existing.setCriminalId(updates.getCriminalId());
        existing.setGender(updates.getGender());
        existing.setPhoneNumber(updates.getPhoneNumber());
        existing.setCrimeCategory(updates.getCrimeCategory());
        existing.setCrimeDescription(updates.getCrimeDescription());
        existing.setFirNumber(updates.getFirNumber());
        existing.setArrestDate(updates.getArrestDate());
        existing.setPoliceStation(updates.getPoliceStation());
        existing.setCurrentStatus(updates.getCurrentStatus());

        Person saved = personService.save(existing);
        auditService.log(auth.getName(), "UPDATE_PERSON", id, null, req.getRemoteAddr());
        return PersonResponse.from(saved);
    }

    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public PersonResponse updatePhoto(@PathVariable Long id, @RequestParam MultipartFile photo,
                                      Authentication auth, HttpServletRequest req) throws Exception {
        Person p = personService.findById(id);
        validateImageUpload(photo);

        String previousPhotoPath = p.getPhotoPath();
        String path = fileStorageService.store(photo);
        p.setPhotoPath(path);
        try {
            p.setFaceEmbedding(personService.extractFaceEmbedding(path));
        } catch (RuntimeException e) {
            p.setFaceEmbedding(null);
            log.warn("Updated photo without a face embedding for person {}: {}", id, e.getMessage());
        }

        Person saved = personService.save(p);
        if (previousPhotoPath != null && !previousPhotoPath.equals(path)) {
            try {
                java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(previousPhotoPath));
            } catch (Exception e) {
                log.warn("Could not remove replaced photo for person {}", id, e);
            }
        }
        auditService.log(auth.getName(), "UPDATE_PERSON_PHOTO", id, null, req.getRemoteAddr());
        return PersonResponse.from(saved);
    }

    @PostMapping("/clean-orphans")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> cleanOrphans(Authentication auth, HttpServletRequest req) {
        List<Person> activePersons = personService.findAll();
        java.util.Set<String> activePaths = new java.util.HashSet<>();
        for (Person p : activePersons) {
            if (p.getPhotoPath() != null) {
                activePaths.add(new java.io.File(p.getPhotoPath()).getAbsolutePath());
            }
        }
        
        java.io.File uploadsDir = fileStorageService.getUploadDirectory().toFile();
        java.io.File[] files = uploadsDir.listFiles();
        int deletedCount = 0;
        if (files != null) {
            for (java.io.File f : files) {
                if (f.isFile()) {
                    String absPath = f.getAbsolutePath();
                    if (!activePaths.contains(absPath)) {
                        if (f.delete()) {
                            deletedCount++;
                        }
                    }
                }
            }
        }
        auditService.log(auth.getName(), "CLEAN_ORPHAN_PHOTOS", null, "deleted=" + deletedCount, req.getRemoteAddr());
        return ResponseEntity.ok("Deleted " + deletedCount + " orphaned upload files.");
    }

    @PostMapping("/convert-existing-photos")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> convertExistingPhotos(Authentication auth, HttpServletRequest req) {
        int convertedCount = personService.convertExistingPhotosToWebP();
        auditService.log(auth.getName(), "CONVERT_EXISTING_PHOTOS", null,
                "converted=" + convertedCount, req.getRemoteAddr());
        return ResponseEntity.ok("Converted " + convertedCount + " existing photos to WebP.");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth, HttpServletRequest req) {
        personService.delete(id);
        auditService.log(auth.getName(), "DELETE_PERSON", id, null, req.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    private void validateImageUpload(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            throw new IllegalArgumentException("No file uploaded. Please select a photograph.");
        }
        
        if (photo.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("File size exceeds the maximum limit of 5MB.");
        }
        
        String contentType = photo.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png") && !contentType.equals("image/jpg") && !contentType.equals("image/webp"))) {
            throw new IllegalArgumentException("Unsupported file type. Only JPG, JPEG, PNG, or WEBP are allowed.");
        }
    }
}
