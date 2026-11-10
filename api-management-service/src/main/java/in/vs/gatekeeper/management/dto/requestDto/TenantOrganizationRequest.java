package in.vs.gatekeeper.management.dto.requestDto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TenantOrganizationRequest {

    @NotBlank
    @Size(max = 150)
    private String name;

    // Optional profile fields. null = leave unchanged, blank = clear the value.
    @Size(max = 1000)
    private String description;

    @Email
    @Size(max = 255)
    private String contactEmail;

    @Size(max = 30)
    @Pattern(regexp = "^[0-9+() -]*$",
            message = "contactPhone may contain only digits, spaces and + ( ) -")
    private String contactPhone;
}
