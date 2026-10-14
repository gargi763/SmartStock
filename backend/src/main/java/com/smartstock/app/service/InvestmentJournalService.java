package com.smartstock.app.service;

import com.smartstock.app.domain.InvestmentJournalEntry;
import com.smartstock.app.domain.Transaction;
import com.smartstock.app.domain.TransactionStatus;
import com.smartstock.app.domain.User;
import com.smartstock.app.dto.InvestmentJournalEntryRequest;
import com.smartstock.app.dto.InvestmentJournalEntryResponse;
import com.smartstock.app.dto.InvestmentJournalTradeResponse;
import com.smartstock.app.exception.ResourceNotFoundException;
import com.smartstock.app.repository.InvestmentJournalEntryRepository;
import com.smartstock.app.repository.TransactionRepository;
import com.smartstock.app.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
public class InvestmentJournalService {

    private final InvestmentJournalEntryRepository journalRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public InvestmentJournalService(InvestmentJournalEntryRepository journalRepository,
                                    UserRepository userRepository,
                                    TransactionRepository transactionRepository) {
        this.journalRepository = journalRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public List<InvestmentJournalEntryResponse> getEntries(String username) {
        return journalRepository.findAllByUser_UsernameOrderByUpdatedAtDesc(username).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InvestmentJournalTradeResponse> getCompletedTrades(String username) {
        return transactionRepository.findByPortfolio_User_UsernameAndStatusOrderByExecutedAtDesc(
                        username, TransactionStatus.COMPLETED).stream()
                .map(transaction -> new InvestmentJournalTradeResponse(
                        transaction.getId(),
                        transaction.getStock().getSymbol(),
                        transaction.getTradeType(),
                        transaction.getExecutedAt()))
                .toList();
    }

    @Transactional
    public InvestmentJournalEntryResponse createEntry(String username, InvestmentJournalEntryRequest request) {
        User user = findUser(username);
        Transaction transaction = findCompletedTrade(username, request.transactionId());
        InvestmentJournalEntry entry = new InvestmentJournalEntry(
                user,
                transaction,
                request.title(),
                request.thesis(),
                request.lessonsLearned(),
                normalizeTags(request.tags()));
        return toResponse(journalRepository.save(entry));
    }

    @Transactional
    public InvestmentJournalEntryResponse updateEntry(
            String username, Long entryId, InvestmentJournalEntryRequest request) {
        InvestmentJournalEntry entry = findEntry(username, entryId);
        Transaction transaction = findCompletedTrade(username, request.transactionId());
        entry.update(transaction, request.title(), request.thesis(),
                request.lessonsLearned(), normalizeTags(request.tags()));
        return toResponse(journalRepository.save(entry));
    }

    @Transactional
    public void deleteEntry(String username, Long entryId) {
        journalRepository.delete(findEntry(username, entryId));
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private InvestmentJournalEntry findEntry(String username, Long entryId) {
        return journalRepository.findByIdAndUser_Username(entryId, username)
                .orElseThrow(() -> new ResourceNotFoundException("Journal entry not found"));
    }

    private Transaction findCompletedTrade(String username, Long transactionId) {
        if (transactionId == null) {
            return null;
        }
        return transactionRepository.findByIdAndPortfolio_User_UsernameAndStatus(
                        transactionId, username, TransactionStatus.COMPLETED)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Completed transaction not found for this user"));
    }

    private Set<String> normalizeTags(List<String> requestedTags) {
        if (requestedTags == null) {
            return Set.of();
        }
        TreeSet<String> tags = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        requestedTags.stream()
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .forEach(tags::add);
        return tags;
    }

    private InvestmentJournalEntryResponse toResponse(InvestmentJournalEntry entry) {
        Transaction transaction = entry.getTransaction();
        return new InvestmentJournalEntryResponse(
                entry.getId(),
                entry.getTitle(),
                entry.getThesis(),
                entry.getLessonsLearned(),
                entry.getTags().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(),
                entry.getCreatedAt(),
                entry.getUpdatedAt(),
                transaction == null ? null : transaction.getId(),
                transaction == null ? null : transaction.getStock().getSymbol(),
                transaction == null ? null : transaction.getTradeType(),
                transaction == null ? null : transaction.getExecutedAt());
    }
}
