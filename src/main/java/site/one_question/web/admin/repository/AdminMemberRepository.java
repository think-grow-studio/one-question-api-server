package site.one_question.web.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import site.one_question.api.member.domain.Member;

public interface AdminMemberRepository extends JpaRepository<Member, Long> {

    @Query("SELECT COUNT(m) FROM Member m WHERE m.id != 1")
    long countExcludingAdmin();
}
