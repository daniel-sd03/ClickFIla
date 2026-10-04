package sodresoftwares.barbearia.model.user;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@EntityListeners(AuditingEntityListener.class)
public class User implements UserDetails {

	@Id 
	@GeneratedValue(strategy = GenerationType.UUID)
	private String id;

	@Column(unique = true, nullable = false)
	private String login;

	@Column(nullable = false)
	private String password;

	@Column(nullable = false)
	private String name;

	@Column(name = "phone")
	private String phone;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private UserRole role;

	@Column(name = "google_id", unique = true, nullable = true)
	private String googleId;

	@Builder.Default
	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;

	@Builder.Default
	@Column(name = "tutorial_completed", nullable = false)
	private Boolean tutorialCompleted = false;

	@Column(name = "email_verified", nullable = false)
	private boolean emailVerified = false;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(name = "updated_at")
	private Instant updatedAt;

	@CreatedBy
	@Column(name = "created_by", updatable = false)
	private String createdBy;

	@LastModifiedBy
	@Column(name = "updated_by")
	private String updatedBy;

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + this.role.name()));
	}

	@Override
	public String getUsername() {
		return login;
	}
}
 