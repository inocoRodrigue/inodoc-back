package com.esn.inodoc.repo.review;

import com.esn.inodoc.domain.review.ReferenceDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReferenceDocumentRepository extends JpaRepository<ReferenceDocument, UUID> {
    List<ReferenceDocument> findAllByOrderByCategoryAscTitleAsc();
}
