package site.one_question.member.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import site.one_question.global.common.domain.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "member")
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 255,nullable = true)
    private String email;

    @Column(name = "full_name", length = 100,nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name="provider",nullable = false, length = 20)
    private AuthSocialProvider provider;

    @Column(name = "provider_id", nullable = false, length = 255)
    private String providerId;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MemberPermission permission;

    @Column(nullable = false, length = 20)
    private String locale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static Member create(
            String email,
            String fullName,
            AuthSocialProvider provider,
            String providerId,
            String locale
    ) {
        return new Member(
                null,
                email,
                fullName,
                provider,
                providerId,
                Instant.now(),
                MemberPermission.FREE,
                locale,
                MemberStatus.ACTIVE,
                null
        );
    }

    public void updateProfile(String email, String fullName, String locale) {
        this.email = email;
        this.fullName = fullName;
        this.locale = locale;
    }

    public boolean isPremium() {
        return this.permission == MemberPermission.PREMIUM;
    }
}
