package ph.edu.pup.reservation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ph.edu.pup.reservation.entity.ResourceType;

public class ResourceRequestDTO {

    @NotBlank(message = "Resource name cannot be blank")
    private String name;

    @NotNull(message = "Resource type is required")
    private ResourceType type;

    @Min(value = 1, message = "Total quantity must be at least 1")
    private int totalQuantity = 1;

    public ResourceRequestDTO() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ResourceType getType() { return type; }
    public void setType(ResourceType type) { this.type = type; }

    public int getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(int totalQuantity) { this.totalQuantity = totalQuantity; }
}