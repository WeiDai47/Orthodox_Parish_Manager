package com.example.orthodox_prm;

import com.example.orthodox_prm.model.Parishioner;
import com.example.orthodox_prm.Enum.MembershipStatus;
import com.example.orthodox_prm.ParishionerFilterCriteria;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

public class ParishionerSpecification {
    public static Specification<Parishioner> filterBy(ParishionerFilterCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.getStatus()));
            }
            if (criteria.isMissingBaptismDate()) {
                predicates.add(cb.isNull(root.get("baptismDate")));
            }
            if (criteria.getNameDayMonth() != null) {
                // Extracts month from the nameDay LocalDate field - only if nameDay is not null
                predicates.add(cb.and(
                        cb.isNotNull(root.get("nameDay")),
                        cb.equal(cb.function("MONTH", Integer.class, root.get("nameDay")), criteria.getNameDayMonth())
                ));
            }
            if (criteria.getSponsorId() != null) {
                // Handle null godfather/godmother gracefully
                predicates.add(cb.or(
                        cb.and(
                                cb.isNotNull(root.get("godfather")),
                                cb.equal(root.get("godfather").get("id"), criteria.getSponsorId())
                        ),
                        cb.and(
                                cb.isNotNull(root.get("godmother")),
                                cb.equal(root.get("godmother").get("id"), criteria.getSponsorId())
                        )
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}