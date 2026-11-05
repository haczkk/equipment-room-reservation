package ph.edu.pup.reservation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ph.edu.pup.reservation.dto.ReservationRequestDTO;
import ph.edu.pup.reservation.entity.*;
import ph.edu.pup.reservation.repository.*;
import ph.edu.pup.reservation.service.ReservationService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ResourceItemRepository resourceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private ReservationService reservationService;

    private User samplePresident;
    private ResourceItem sampleRoom;

    @BeforeEach
    void setUp() {
        samplePresident = new User();
        samplePresident.setId(2L);
        samplePresident.setFullName("Juan Dela Cruz");
        samplePresident.setEmail("juan@pup.edu.ph");
        samplePresident.setRole(Role.CLASS_PRESIDENT);
        samplePresident.setSection("BSIT 2-1");

        sampleRoom = new ResourceItem();
        sampleRoom.setId(1L);
        sampleRoom.setName("AVR - Audio Visual Room");
        sampleRoom.setType(ResourceType.ROOM);
        sampleRoom.setTotalQuantity(1);
        sampleRoom.setAvailableQuantity(1);
        sampleRoom.setStatus(ResourceStatus.AVAILABLE);

        // Safely mock both findById and findWithLockById for any ID passed
        lenient().when(userRepository.findById(anyLong())).thenReturn(Optional.of(samplePresident));
        lenient().when(resourceRepository.findWithLockById(anyLong())).thenReturn(Optional.of(sampleRoom));
        lenient().when(resourceRepository.findById(anyLong())).thenReturn(Optional.of(sampleRoom));
        lenient().when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Test 1: Booking within 7 days with no conflict should AUTO-APPROVE and generate token")
    void testCreateReservation_AutoApproved() {
        ReservationRequestDTO dto = new ReservationRequestDTO();
        dto.setPresidentId(2L);
        dto.setResourceId(1L);
        dto.setReservationDate(LocalDate.now().plusDays(1));
        dto.setStartTime(LocalTime.of(9, 0));
        dto.setEndTime(LocalTime.of(11, 0));
        dto.setProfessorName("Prof. Santos");
        dto.setPurpose("INTE 201 Defense");
        dto.setRequestedQuantity(1);

        when(reservationRepository.findByResourceIdAndReservationDateAndStatusIn(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());

        // Act
        Reservation result = reservationService.createReservation(dto);

        // Assert
        assertNotNull(result);
        assertEquals(ReservationStatus.AUTO_APPROVED, result.getStatus());
        assertNotNull(result.getPickupToken(), "Unguessable token should be generated");
        assertTrue(result.getPickupToken().startsWith("TCK-"));
        assertNotNull(result.getTokenExpiresAt(), "Token expiration grace period should be set");
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Test 2: Double-booking the same room at the same time must mark request as PENDING")
    void testCreateReservation_Conflict_RoutesToPending() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        ReservationRequestDTO dto = new ReservationRequestDTO();
        dto.setPresidentId(2L);
        dto.setResourceId(1L);
        dto.setReservationDate(tomorrow);
        dto.setStartTime(LocalTime.of(10, 0)); // Overlaps with 09:00 - 11:00
        dto.setEndTime(LocalTime.of(12, 0));
        dto.setProfessorName("Prof. Reyes");
        dto.setPurpose("Conflict Test");
        dto.setRequestedQuantity(1);

        Reservation existing = new Reservation();
        existing.setStartTime(LocalTime.of(9, 0));
        existing.setEndTime(LocalTime.of(11, 0));
        existing.setStatus(ReservationStatus.AUTO_APPROVED);

        when(reservationRepository.findByResourceIdAndReservationDateAndStatusIn(anyLong(), any(), any()))
                .thenReturn(List.of(existing));

        // Act
        Reservation result = reservationService.createReservation(dto);

        // Assert
        assertNotNull(result);
        assertEquals(ReservationStatus.PENDING, result.getStatus(), "Conflicting booking must be set to PENDING");
        assertNull(result.getPickupToken(), "Pending booking must not have a pickup token yet");
    }

    @Test
    @DisplayName("Test 3: Booking beyond the 7-day restriction must throw IllegalArgumentException")
    void testCreateReservation_Beyond7Days_ThrowsException() {
        ReservationRequestDTO dto = new ReservationRequestDTO();
        dto.setReservationDate(LocalDate.now().plusDays(10)); // 10 days in the future
        dto.setStartTime(LocalTime.of(9, 0));
        dto.setEndTime(LocalTime.of(11, 0));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            reservationService.createReservation(dto);
        });

        assertEquals("Reservations must be within the next 7 days.", ex.getMessage());
    }

    @Test
    @DisplayName("Test 4: Booking outside campus operating hours (07:00 - 19:00) must throw IllegalArgumentException")
    void testCreateReservation_OutsideOperatingHours_ThrowsException() {
        ReservationRequestDTO dto = new ReservationRequestDTO();
        dto.setReservationDate(LocalDate.now().plusDays(1));
        dto.setStartTime(LocalTime.of(5, 0)); // 5:00 AM (Closed)
        dto.setEndTime(LocalTime.of(7, 0));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            reservationService.createReservation(dto);
        });

        assertTrue(ex.getMessage().contains("operating hours"));
    }
}