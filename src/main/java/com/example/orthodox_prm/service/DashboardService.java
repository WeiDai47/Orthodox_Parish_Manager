package com.example.orthodox_prm.service;

import com.example.orthodox_prm.Enum.MembershipStatus;
import com.example.orthodox_prm.dto.UpcomingEvent;
import com.example.orthodox_prm.model.Parishioner;
import com.example.orthodox_prm.model.ScheduledEvent;
import com.example.orthodox_prm.repository.ParishionerRepository;
import com.example.orthodox_prm.repository.HouseholdRepository;
import com.example.orthodox_prm.repository.ScheduledEventRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.*;

@Service
public class DashboardService {

    private final ParishionerRepository parishionerRepo;
    private final HouseholdRepository householdRepo;
    private final ScheduledEventRepository scheduledEventRepo;

    public DashboardService(ParishionerRepository parishionerRepo, HouseholdRepository householdRepo,
                          ScheduledEventRepository scheduledEventRepo) {
        this.parishionerRepo = parishionerRepo;
        this.householdRepo = householdRepo;
        this.scheduledEventRepo = scheduledEventRepo;
    }

    public Map<String, Object> getPriestStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalMembers", parishionerRepo.count());
        stats.put("totalHouseholds", householdRepo.count());

        // Specific counts for the dashboard cards
        stats.put("visitorCount", parishionerRepo.countByStatus(MembershipStatus.VISITOR));
        stats.put("catechumenCount", parishionerRepo.countByStatus(MembershipStatus.CATECHUMEN));

        // Find everyone with a Name Day today
        stats.put("todaysNameDays", parishionerRepo.findByNameDay(LocalDate.now()));

        // Get upcoming events for next 7 days
        List<UpcomingEvent> upcomingEvents = getUpcomingEvents();
        stats.put("upcomingEvents", upcomingEvents);

        return stats;
    }

    /**
     * Get all upcoming events for the next 7 days (including today)
     * Includes: name days, baptism days, sacraments, and events
     * Sorted by date and time (soonest first)
     */
    public List<UpcomingEvent> getUpcomingEvents() {
        List<UpcomingEvent> events = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysFromNow = today.plusDays(7);

        // 1. Get name days (anniversaries) for next 7 days
        events.addAll(getNameDaysInRange(today, sevenDaysFromNow));

        // 2. Get baptism day anniversaries for next 7 days
        events.addAll(getBaptismDaysInRange(today, sevenDaysFromNow));

        // 3. Get birthdays for next 7 days
        events.addAll(getBirthdaysInRange(today, sevenDaysFromNow));

        // 4. Get wedding anniversaries for next 7 days
        events.addAll(getWeddingAnniversariesInRange(today, sevenDaysFromNow));

        // 5. Get chrismation day anniversaries for next 7 days
        events.addAll(getChrismationDaysInRange(today, sevenDaysFromNow));

        // 6. Get death anniversaries for next 7 days
        events.addAll(getDeathAnniversariesInRange(today, sevenDaysFromNow));

        // 7. Get scheduled sacraments (new baptisms and chrismations) for next 7 days
        events.addAll(getScheduledSacraments(today, sevenDaysFromNow));

        // 8. Get scheduled events for next 7 days
        events.addAll(getScheduledEvents(today, sevenDaysFromNow));

        // Sort by date and time (soonest first)
        Collections.sort(events);

        return events;
    }

    /**
     * Get name days (anniversaries) for the specified date range
     */
    private List<UpcomingEvent> getNameDaysInRange(LocalDate startDate, LocalDate endDate) {
        List<UpcomingEvent> events = new ArrayList<>();
        List<Parishioner> allParishioners = parishionerRepo.findAll();

        MonthDay startMonthDay = MonthDay.from(startDate);
        MonthDay endMonthDay = MonthDay.from(endDate);

        for (Parishioner p : allParishioners) {
            if (p.getNameDay() == null) continue;

            MonthDay pNameDay = MonthDay.from(p.getNameDay());

            // Check if name day falls within the range
            boolean isInRange;
            if (startMonthDay.isBefore(endMonthDay)) {
                isInRange = !pNameDay.isBefore(startMonthDay) && !pNameDay.isAfter(endMonthDay);
            } else {
                // Range wraps around year (unlikely in 7-day window, but handle it)
                isInRange = !pNameDay.isBefore(startMonthDay) || !pNameDay.isAfter(endMonthDay);
            }

            if (isInRange) {
                LocalDate eventDate = pNameDay.atYear(startDate.getYear());
                if (eventDate.isBefore(startDate)) {
                    eventDate = pNameDay.atYear(startDate.getYear() + 1);
                }

                String fullName = p.getFirstName() + " " + p.getLastName();
                if (p.getNameSuffix() != null && !p.getNameSuffix().trim().isEmpty()) {
                    fullName += " " + p.getNameSuffix();
                }

                UpcomingEvent event = new UpcomingEvent();
                event.setTitle("Name Day: " + (p.getBaptismalName() != null ? p.getBaptismalName() : p.getFirstName()));
                event.setParishionerName(fullName);
                event.setDate(eventDate);
                event.setTime(null); // Name days are all-day
                event.setType("NAME_DAY");
                event.setDescription(p.getPatronSaint());
                event.setParishionerId(p.getId());
                events.add(event);
            }
        }

        return events;
    }

    /**
     * Get baptism days for the specified date range
     */
    private List<UpcomingEvent> getBaptismDaysInRange(LocalDate startDate, LocalDate endDate) {
        List<UpcomingEvent> events = new ArrayList<>();
        List<Parishioner> allParishioners = parishionerRepo.findAll();

        MonthDay startMonthDay = MonthDay.from(startDate);
        MonthDay endMonthDay = MonthDay.from(endDate);

        for (Parishioner p : allParishioners) {
            if (p.getBaptismDate() == null) continue;

            MonthDay pBaptismDay = MonthDay.from(p.getBaptismDate());

            // Check if baptism day anniversary falls within the range
            boolean isInRange;
            if (startMonthDay.isBefore(endMonthDay)) {
                isInRange = !pBaptismDay.isBefore(startMonthDay) && !pBaptismDay.isAfter(endMonthDay);
            } else {
                isInRange = !pBaptismDay.isBefore(startMonthDay) || !pBaptismDay.isAfter(endMonthDay);
            }

            if (isInRange) {
                LocalDate eventDate = pBaptismDay.atYear(startDate.getYear());
                if (eventDate.isBefore(startDate)) {
                    eventDate = pBaptismDay.atYear(startDate.getYear() + 1);
                }

                String fullName = p.getFirstName() + " " + p.getLastName();
                if (p.getNameSuffix() != null && !p.getNameSuffix().trim().isEmpty()) {
                    fullName += " " + p.getNameSuffix();
                }

                UpcomingEvent event = new UpcomingEvent();
                event.setTitle("Baptism Anniversary: " + p.getFirstName());
                event.setParishionerName(fullName);
                event.setDate(eventDate);
                event.setTime(null); // Baptism days are all-day
                event.setType("BAPTISM_DAY");
                event.setDescription("Baptized on " + p.getBaptismDate());
                event.setParishionerId(p.getId());
                events.add(event);
            }
        }

        return events;
    }

    /**
     * Get birthdays for the specified date range
     */
    private List<UpcomingEvent> getBirthdaysInRange(LocalDate startDate, LocalDate endDate) {
        List<UpcomingEvent> events = new ArrayList<>();
        List<Parishioner> allParishioners = parishionerRepo.findAll();

        MonthDay startMonthDay = MonthDay.from(startDate);
        MonthDay endMonthDay = MonthDay.from(endDate);

        for (Parishioner p : allParishioners) {
            if (p.getBirthday() == null) continue;

            MonthDay pBirthday = MonthDay.from(p.getBirthday());

            // Check if birthday falls within the range
            boolean isInRange;
            if (startMonthDay.isBefore(endMonthDay)) {
                isInRange = !pBirthday.isBefore(startMonthDay) && !pBirthday.isAfter(endMonthDay);
            } else {
                isInRange = !pBirthday.isBefore(startMonthDay) || !pBirthday.isAfter(endMonthDay);
            }

            if (isInRange) {
                LocalDate eventDate = pBirthday.atYear(startDate.getYear());
                if (eventDate.isBefore(startDate)) {
                    eventDate = pBirthday.atYear(startDate.getYear() + 1);
                }

                String fullName = p.getFirstName() + " " + p.getLastName();
                if (p.getNameSuffix() != null && !p.getNameSuffix().trim().isEmpty()) {
                    fullName += " " + p.getNameSuffix();
                }

                UpcomingEvent event = new UpcomingEvent();
                event.setTitle("Birthday: " + p.getFirstName());
                event.setParishionerName(fullName);
                event.setDate(eventDate);
                event.setTime(null); // Birthdays are all-day
                event.setType("BIRTHDAY");
                event.setDescription("Born on " + p.getBirthday());
                event.setParishionerId(p.getId());
                events.add(event);
            }
        }

        return events;
    }

    /**
     * Get wedding anniversaries for the specified date range
     */
    private List<UpcomingEvent> getWeddingAnniversariesInRange(LocalDate startDate, LocalDate endDate) {
        List<UpcomingEvent> events = new ArrayList<>();
        List<Parishioner> allParishioners = parishionerRepo.findAll();

        MonthDay startMonthDay = MonthDay.from(startDate);
        MonthDay endMonthDay = MonthDay.from(endDate);

        for (Parishioner p : allParishioners) {
            if (p.getMarriageDate() == null) continue;

            MonthDay pMarriageDay = MonthDay.from(p.getMarriageDate());

            // Check if marriage anniversary falls within the range
            boolean isInRange;
            if (startMonthDay.isBefore(endMonthDay)) {
                isInRange = !pMarriageDay.isBefore(startMonthDay) && !pMarriageDay.isAfter(endMonthDay);
            } else {
                isInRange = !pMarriageDay.isBefore(startMonthDay) || !pMarriageDay.isAfter(endMonthDay);
            }

            if (isInRange) {
                LocalDate eventDate = pMarriageDay.atYear(startDate.getYear());
                if (eventDate.isBefore(startDate)) {
                    eventDate = pMarriageDay.atYear(startDate.getYear() + 1);
                }

                String spouseName;
                if (p.getSpouse() != null) {
                    spouseName = p.getSpouse().getFirstName() + " " + p.getSpouse().getLastName();
                    if (p.getSpouse().getNameSuffix() != null && !p.getSpouse().getNameSuffix().trim().isEmpty()) {
                        spouseName += " " + p.getSpouse().getNameSuffix();
                    }
                } else {
                    spouseName = (p.getManualSpouseName() != null ? p.getManualSpouseName() : "Unknown");
                }

                String personName = p.getFirstName() + " " + p.getLastName();
                if (p.getNameSuffix() != null && !p.getNameSuffix().trim().isEmpty()) {
                    personName += " " + p.getNameSuffix();
                }

                UpcomingEvent event = new UpcomingEvent();
                event.setTitle("Wedding Anniversary: " + p.getFirstName());
                event.setParishionerName(personName + " & " + spouseName);
                event.setDate(eventDate);
                event.setTime(null); // Wedding anniversaries are all-day
                event.setType("WEDDING_ANNIVERSARY");
                event.setDescription("Married on " + p.getMarriageDate());
                event.setParishionerId(p.getId());
                events.add(event);
            }
        }

        return events;
    }

    /**
     * Get chrismation day anniversaries for the specified date range
     */
    private List<UpcomingEvent> getChrismationDaysInRange(LocalDate startDate, LocalDate endDate) {
        List<UpcomingEvent> events = new ArrayList<>();
        List<Parishioner> allParishioners = parishionerRepo.findAll();

        MonthDay startMonthDay = MonthDay.from(startDate);
        MonthDay endMonthDay = MonthDay.from(endDate);

        for (Parishioner p : allParishioners) {
            if (p.getChrismationDate() == null) continue;

            MonthDay pChrismationDay = MonthDay.from(p.getChrismationDate());

            // Check if chrismation day anniversary falls within the range
            boolean isInRange;
            if (startMonthDay.isBefore(endMonthDay)) {
                isInRange = !pChrismationDay.isBefore(startMonthDay) && !pChrismationDay.isAfter(endMonthDay);
            } else {
                isInRange = !pChrismationDay.isBefore(startMonthDay) || !pChrismationDay.isAfter(endMonthDay);
            }

            if (isInRange) {
                LocalDate eventDate = pChrismationDay.atYear(startDate.getYear());
                if (eventDate.isBefore(startDate)) {
                    eventDate = pChrismationDay.atYear(startDate.getYear() + 1);
                }

                String fullName = p.getFirstName() + " " + p.getLastName();
                if (p.getNameSuffix() != null && !p.getNameSuffix().trim().isEmpty()) {
                    fullName += " " + p.getNameSuffix();
                }

                UpcomingEvent event = new UpcomingEvent();
                event.setTitle("Chrismation Anniversary: " + p.getFirstName());
                event.setParishionerName(fullName);
                event.setDate(eventDate);
                event.setTime(null); // Chrismation days are all-day
                event.setType("CHRISMATION_DAY");
                event.setDescription("Chrismated on " + p.getChrismationDate());
                event.setParishionerId(p.getId());
                events.add(event);
            }
        }

        return events;
    }

    /**
     * Get death anniversaries for the specified date range
     */
    private List<UpcomingEvent> getDeathAnniversariesInRange(LocalDate startDate, LocalDate endDate) {
        List<UpcomingEvent> events = new ArrayList<>();
        List<Parishioner> allParishioners = parishionerRepo.findAll();

        MonthDay startMonthDay = MonthDay.from(startDate);
        MonthDay endMonthDay = MonthDay.from(endDate);

        for (Parishioner p : allParishioners) {
            if (p.getDeathDate() == null) continue;

            MonthDay pDeathDay = MonthDay.from(p.getDeathDate());

            // Check if death day anniversary falls within the range
            boolean isInRange;
            if (startMonthDay.isBefore(endMonthDay)) {
                isInRange = !pDeathDay.isBefore(startMonthDay) && !pDeathDay.isAfter(endMonthDay);
            } else {
                isInRange = !pDeathDay.isBefore(startMonthDay) || !pDeathDay.isAfter(endMonthDay);
            }

            if (isInRange) {
                LocalDate eventDate = pDeathDay.atYear(startDate.getYear());
                if (eventDate.isBefore(startDate)) {
                    eventDate = pDeathDay.atYear(startDate.getYear() + 1);
                }

                String fullName = p.getFirstName() + " " + p.getLastName();
                if (p.getNameSuffix() != null && !p.getNameSuffix().trim().isEmpty()) {
                    fullName += " " + p.getNameSuffix();
                }

                UpcomingEvent event = new UpcomingEvent();
                event.setTitle("Repose Anniversary: " + p.getFirstName());
                event.setParishionerName(fullName);
                event.setDate(eventDate);
                event.setTime(null); // Death anniversaries are all-day
                event.setType("DEATH_ANNIVERSARY");
                event.setDescription("Reposed on " + p.getDeathDate());
                event.setParishionerId(p.getId());
                events.add(event);
            }
        }

        return events;
    }

    /**
     * Get scheduled sacraments for the specified date range
     */
    private List<UpcomingEvent> getScheduledSacraments(LocalDate startDate, LocalDate endDate) {
        List<UpcomingEvent> events = new ArrayList<>();
        List<ScheduledEvent> sacraments = scheduledEventRepo.findUpcomingSacraments(startDate);

        for (ScheduledEvent sacrament : sacraments) {
            if (sacrament.getEventDate().isAfter(endDate)) {
                continue;
            }

            // Get all participants
            List<String> participantNames = new ArrayList<>();
            if (sacrament.getParticipants() != null && !sacrament.getParticipants().isEmpty()) {
                sacrament.getParticipants().forEach(p ->
                    participantNames.add(p.getParishioner().getFirstName() + " " + p.getParishioner().getLastName())
                );
            }

            UpcomingEvent event = new UpcomingEvent();
            event.setTitle(sacrament.getSacramentType() + ": " + sacrament.getEventTitle());
            event.setParishionerName(String.join(", ", participantNames));
            event.setDate(sacrament.getEventDate());
            event.setTime(sacrament.getStartTime());
            event.setType("SACRAMENT");
            event.setDescription(sacrament.getEventDescription());
            event.setEventId(sacrament.getId());
            events.add(event);
        }

        return events;
    }

    /**
     * Get scheduled events for the specified date range
     */
    private List<UpcomingEvent> getScheduledEvents(LocalDate startDate, LocalDate endDate) {
        List<UpcomingEvent> events = new ArrayList<>();
        List<ScheduledEvent> scheduledEvents = scheduledEventRepo.findUpcomingEvents(startDate);

        for (ScheduledEvent scheduledEvent : scheduledEvents) {
            if (scheduledEvent.getEventDate().isAfter(endDate) || scheduledEvent.getSacramentType() != null) {
                // Skip sacraments (we get those separately) and events beyond the range
                continue;
            }

            // Get all participants
            List<String> participantNames = new ArrayList<>();
            if (scheduledEvent.getParticipants() != null && !scheduledEvent.getParticipants().isEmpty()) {
                scheduledEvent.getParticipants().forEach(p ->
                    participantNames.add(p.getParishioner().getFirstName() + " " + p.getParishioner().getLastName())
                );
            }

            UpcomingEvent event = new UpcomingEvent();
            event.setTitle(scheduledEvent.getEventTitle());
            event.setParishionerName(String.join(", ", participantNames));
            event.setDate(scheduledEvent.getEventDate());
            event.setTime(scheduledEvent.getStartTime());
            event.setType("EVENT");
            event.setDescription(scheduledEvent.getEventDescription());
            event.setEventId(scheduledEvent.getId());
            events.add(event);
        }

        return events;
    }
}