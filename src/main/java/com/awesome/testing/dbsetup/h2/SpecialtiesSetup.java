package com.awesome.testing.dbsetup.h2;

import com.awesome.testing.service.SpecialtiesService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class SpecialtiesSetup {

    static final Set<String> SPECIALTIES = Set.of(
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
            "Otolaryngologist",
            "Physiotherapist"
    );

    private final SpecialtiesService specialtiesService;

    public void setupSpecialties() {
        SPECIALTIES.forEach(specialtiesService::addSpecialty);
    }

}
