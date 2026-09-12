package com.facedb.dto;

import com.facedb.model.Person;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class PersonResponse {
    public Long id;
    public String fullName;
    public LocalDate dateOfBirth;
    public String address;
    public String otherDetails;
    public String photoUrl;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    public String createdBy;
    public Double matchScore;

    public String criminalId;
    public String gender;
    public Integer age;
    public String phoneNumber;
    public String crimeCategory;
    public String crimeDescription;
    public String firNumber;
    public LocalDate arrestDate;
    public String policeStation;
    public String currentStatus;

    public static PersonResponse from(Person p) {
        PersonResponse r = new PersonResponse();
        r.id = p.getId();
        r.fullName = p.getFullName();
        r.dateOfBirth = p.getDateOfBirth();
        r.address = p.getAddress();
        r.otherDetails = p.getOtherDetails();
        r.photoUrl = p.getPhotoPath() != null ? "/api/persons/" + p.getId() + "/photo" : null;
        r.createdAt = p.getCreatedAt();
        r.updatedAt = p.getUpdatedAt();
        r.createdBy = p.getCreatedBy();

        r.criminalId = p.getCriminalId();
        r.gender = p.getGender();
        r.phoneNumber = p.getPhoneNumber();
        r.crimeCategory = p.getCrimeCategory();
        r.crimeDescription = p.getCrimeDescription();
        r.firNumber = p.getFirNumber();
        r.arrestDate = p.getArrestDate();
        r.policeStation = p.getPoliceStation();
        r.currentStatus = p.getCurrentStatus();

        if (p.getDateOfBirth() != null) {
            r.age = java.time.Period.between(p.getDateOfBirth(), LocalDate.now()).getYears();
        }

        return r;
    }
}
