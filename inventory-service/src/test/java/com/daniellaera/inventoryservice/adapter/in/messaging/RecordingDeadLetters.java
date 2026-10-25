package com.daniellaera.inventoryservice.adapter.in.messaging;

import com.daniellaera.inventoryservice.domain.port.in.ReportDeadLetteredUseCase;

import java.util.ArrayList;
import java.util.List;

/** Records what the listeners' @DltHandler methods report. */
public class RecordingDeadLetters implements ReportDeadLetteredUseCase {

    public record Report(String kind, Long orderId, String userEmail, String error) {}

    private final List<Report> reports = new ArrayList<>();

    @Override
    public void reservationDeadLettered(Long orderId, String userEmail, String error) {
        reports.add(new Report("reservation", orderId, userEmail, error));
    }

    @Override
    public void restoreDeadLettered(Long orderId, String userEmail, String error) {
        reports.add(new Report("restore", orderId, userEmail, error));
    }

    public List<Report> reports() {
        return List.copyOf(reports);
    }
}
