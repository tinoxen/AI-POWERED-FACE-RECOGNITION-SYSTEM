package com.facedb.repository;

import com.facedb.model.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PersonRepository extends JpaRepository<Person, Long> {
    List<Person> findByFullNameContainingIgnoreCase(String name);
    List<Person> findByFullNameContainingIgnoreCaseOrCriminalIdContainingIgnoreCaseOrFirNumberContainingIgnoreCase(
            String fullName, String criminalId, String firNumber);
}
