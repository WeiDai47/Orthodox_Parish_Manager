package com.example.orthodox_prm.repository;

import com.example.orthodox_prm.model.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NoteRepository extends JpaRepository<Note, Long> {

    List<Note> findByParishioner_IdOrderByCreatedAtDesc(Long parishionerId);

    List<Note> findByHousehold_IdOrderByCreatedAtDesc(Long householdId);
}
