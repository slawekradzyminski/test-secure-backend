package com.awesome.testing.dbsetup.h2;

import com.awesome.testing.service.DoctorTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class DoctorTypesSetup {

    static final Set<String> DOCTOR_SPECIALTIES = Set.of(
            "Pediatrician",
            "Psychiatrist",
            "Cardiologist",
            "Endocrinologist"
    );

    static final Set<String> SPECIALTIES = Set.of(
            "Neurologist",
            "Gastroenterologist",
            "Dermatologist",
            "Radiologist",
            "Pathologist",
            "Surgeon",
            "Anesthesiologist",
            "Ophthalmologist",
            "Orthopedist",
            "Urologist",
            "Obstetrician gynecologist",
            "Oncologist",
            "Pulmonologist",
            "Nephrologist",
            "Allergist immunologist",
            "Infectious disease specialist",
            "Rheumatologist",
            "Geriatrician",
            "Sports medicine specialist",
            "Physical medicine rehabilitation",
            "Pain management specialist",
            "Palliative care specialist",
            "Sleep medicine specialist",
            "Plastic surgeon",
            "Otolaryngologist"
    );

    private final DoctorTypeService doctorTypeService;

    public void setupDoctorTypes() {
        DOCTOR_SPECIALTIES.forEach(doctorTypeService::addDoctorType);
        SPECIALTIES.forEach(doctorTypeService::addDoctorType);
    }

}
