package com.awesome.testing.dbsetup;

import com.awesome.testing.service.DoctorTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DoctorTypesSetup {

    private static final List<String> SPECIALTIES = List.of(
            "Pediatrician",
            "Psychiatrist",
            "Cardiologist",
            "Endocrinologist",
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
        SPECIALTIES.forEach(doctorTypeService::addDoctorType);
    }

}
