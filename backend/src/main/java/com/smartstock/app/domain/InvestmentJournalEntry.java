package com.smartstock.app.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "investment_journal_entries")
@Getter
@NoArgsConstructor
public class InvestmentJournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 4000)
    private String thesis;

    @Column(name = "lessons_learned", nullable = false, length = 4000)
    private String lessonsLearned;

    @ElementCollection
    @CollectionTable(
            name = "investment_journal_tags",
            joinColumns = @JoinColumn(name = "journal_entry_id"))
    @Column(name = "tag", nullable = false, length = 40)
    private Set<String> tags = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public InvestmentJournalEntry(User user, Transaction transaction, String title,
                                  String thesis, String lessonsLearned, Set<String> tags) {
        this.user = user;
        update(transaction, title, thesis, lessonsLearned, tags);
    }

    public void update(Transaction transaction, String title, String thesis,
                       String lessonsLearned, Set<String> tags) {
        this.transaction = transaction;
        this.title = title.trim();
        this.thesis = thesis.trim();
        this.lessonsLearned = lessonsLearned.trim();
        this.tags = new LinkedHashSet<>(tags);
    }

    @PrePersist
    void setCreatedAndUpdatedAt() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void setUpdatedAt() {
        updatedAt = LocalDateTime.now();
    }
}
