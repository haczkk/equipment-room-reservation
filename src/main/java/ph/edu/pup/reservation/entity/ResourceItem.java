package ph.edu.pup.reservation.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "resources")
public class ResourceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceType type;

    private int totalQuantity;
    private int availableQuantity;

    // Tracks how many individual units are currently broken/under maintenance
    private int maintenanceQuantity = 0;

    @Enumerated(EnumType.STRING)
    private ResourceStatus status;

    public ResourceItem() {
        this.status = ResourceStatus.AVAILABLE;
        this.maintenanceQuantity = 0;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ResourceType getType() { return type; }
    public void setType(ResourceType type) { this.type = type; }

    public int getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(int totalQuantity) { this.totalQuantity = totalQuantity; }

    public int getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(int availableQuantity) { this.availableQuantity = availableQuantity; }

    public int getMaintenanceQuantity() { return maintenanceQuantity; }
    public void setMaintenanceQuantity(int maintenanceQuantity) { this.maintenanceQuantity = maintenanceQuantity; }

    public ResourceStatus getStatus() { return status; }
    public void setStatus(ResourceStatus status) { this.status = status; }
}