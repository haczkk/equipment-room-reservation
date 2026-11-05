package ph.edu.pup.reservation.dto;

public class ChangePasswordDTO {

    private Long userId;
    private String currentPassword;
    private String newPassword;

    public ChangePasswordDTO() {}

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}