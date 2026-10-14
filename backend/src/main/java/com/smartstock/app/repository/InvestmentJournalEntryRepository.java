package com.smartstock.app.repository;

import com.smartstock.app.domain.InvestmentJournalEntry;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvestmentJournalEntryRepository extends JpaRepository<InvestmentJournalEntry, Long> {

    @EntityGraph(attributePaths = {"transaction", "transaction.stock"})
    List<InvestmentJournalEntry> findAllByUser_UsernameOrderByUpdatedAtDesc(String username);

    @EntityGraph(attributePaths = {"transaction", "transaction.stock"})
    Optional<InvestmentJournalEntry> findByIdAndUser_Username(Long id, String username);
}
