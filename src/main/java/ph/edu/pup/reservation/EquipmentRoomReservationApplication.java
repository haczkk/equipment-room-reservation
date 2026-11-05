package ph.edu.pup.reservation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EquipmentRoomReservationApplication {

    public static void main(String[] args) {
        SpringApplication.run(EquipmentRoomReservationApplication.class, args);
    }
}