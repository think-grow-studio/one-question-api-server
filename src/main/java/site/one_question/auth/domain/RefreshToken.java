package site.one_question.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import site.one_question.global.common.domain.BaseEntity;
import site.one_question.member.domain.Member;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "refresh_token")
public class RefreshToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 512)
    private String token;

    @Column(name = "expired_at", nullable = false)
    private Instant expiredAt;

    public static RefreshToken create(Member member, String token, Instant expiredAt) {
        return new RefreshToken(null, member, token, expiredAt);
    }

    public void updateToken(String token, Instant expiredAt) {
        this.token = token;
        this.expiredAt = expiredAt;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiredAt);
    }

    public boolean matches(String token) {
        return this.token.equals(token);
    }
}
