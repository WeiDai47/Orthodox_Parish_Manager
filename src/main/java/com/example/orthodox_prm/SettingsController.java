package com.example.orthodox_prm;

import com.example.orthodox_prm.model.UserPreferences;
import com.example.orthodox_prm.repository.UserPreferencesRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@Controller
@RequestMapping("/settings")
@Slf4j
@PreAuthorize("hasAnyRole('PRIEST','SECRETARY','VIEWER')")
public class SettingsController {

    private final UserPreferencesRepository userPreferencesRepository;

    // US Timezones
    private static final List<String> US_TIMEZONES = Arrays.asList(
            "America/New_York",         // Eastern
            "America/Detroit",          // Eastern (Michigan)
            "America/Kentucky/Louisville", // Eastern (Kentucky)
            "America/Kentucky/Monticello", // Eastern (Kentucky)
            "America/Kentucky/North_Knox", // Eastern (Kentucky)
            "America/Kentucky/South_Knox", // Eastern (Kentucky)
            "America/North_Dakota/Center", // Central (North Dakota)
            "America/North_Dakota/New_Salem", // Central (North Dakota)
            "America/Chicago",          // Central
            "America/Indiana/Indianapolis", // Eastern (Indiana)
            "America/Indiana/Knox",     // Central (Indiana)
            "America/Indiana/Marengo",  // Eastern (Indiana)
            "America/Indiana/Petersburg", // Eastern (Indiana)
            "America/Indiana/Tell_City", // Central (Indiana)
            "America/Indiana/Vevay",    // Eastern (Indiana)
            "America/Indiana/Vincennes", // Eastern (Indiana)
            "America/Indiana/Winamac",  // Eastern (Indiana)
            "America/Denver",           // Mountain
            "America/Boise",            // Mountain (Idaho)
            "America/Los_Angeles",      // Pacific
            "America/Juneau",           // Alaska
            "America/Anchorage",        // Alaska
            "America/Sitka",            // Alaska
            "America/Metlakatla",       // Alaska
            "America/Yakutat",          // Alaska
            "America/Nome",             // Alaska
            "America/Adak",             // Hawaii-Aleutian
            "Pacific/Honolulu"          // Hawaii
    );

    public SettingsController(UserPreferencesRepository userPreferencesRepository) {
        this.userPreferencesRepository = userPreferencesRepository;
    }

    /**
     * Display settings page
     */
    @GetMapping
    public String showSettings(Model model) {
        String username = getCurrentUsername();
        UserPreferences prefs = userPreferencesRepository.findByUsername(username)
                .orElse(createDefaultPreferences(username));

        model.addAttribute("userPreferences", prefs);
        model.addAttribute("timezones", getGroupedTimezones());

        return "settings";
    }

    /**
     * Save timezone preference
     */
    @PostMapping
    public String saveSettings(@RequestParam String timezone, Model model) {
        String username = getCurrentUsername();

        if (!US_TIMEZONES.contains(timezone)) {
            model.addAttribute("error", "Invalid timezone selected");
            model.addAttribute("timezones", getGroupedTimezones());
            return "settings";
        }

        UserPreferences prefs = userPreferencesRepository.findByUsername(username)
                .orElse(new UserPreferences());
        prefs.setUsername(username);
        prefs.setTimezone(timezone);
        userPreferencesRepository.save(prefs);

        model.addAttribute("userPreferences", prefs);
        model.addAttribute("timezones", getGroupedTimezones());
        model.addAttribute("success", "Timezone updated successfully! Events will now use " + timezone);

        return "settings";
    }

    /**
     * Get the current authenticated username
     */
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof OAuth2User) {
            OAuth2User oAuth2User = (OAuth2User) auth.getPrincipal();
            return oAuth2User.getAttribute("email") != null ?
                    oAuth2User.getAttribute("email") :
                    oAuth2User.getName();
        }

        return auth != null ? auth.getName() : "anonymous";
    }

    /**
     * Create default preferences for a user
     */
    private UserPreferences createDefaultPreferences(String username) {
        UserPreferences prefs = new UserPreferences();
        prefs.setUsername(username);
        prefs.setTimezone("America/New_York");
        return prefs;
    }

    /**
     * Get timezones grouped by region for easier selection
     */
    private java.util.Map<String, List<String>> getGroupedTimezones() {
        java.util.Map<String, List<String>> grouped = new java.util.LinkedHashMap<>();

        grouped.put("Eastern", Arrays.asList(
                "America/New_York",
                "America/Detroit",
                "America/Kentucky/Louisville",
                "America/Indiana/Indianapolis",
                "America/Indiana/Marengo",
                "America/Indiana/Petersburg",
                "America/Indiana/Vevay",
                "America/Indiana/Vincennes",
                "America/Indiana/Winamac"
        ));

        grouped.put("Central", Arrays.asList(
                "America/Chicago",
                "America/Indiana/Knox",
                "America/Indiana/Tell_City",
                "America/North_Dakota/Center",
                "America/North_Dakota/New_Salem"
        ));

        grouped.put("Mountain", Arrays.asList(
                "America/Denver",
                "America/Boise"
        ));

        grouped.put("Pacific", Arrays.asList(
                "America/Los_Angeles"
        ));

        grouped.put("Alaska", Arrays.asList(
                "America/Juneau",
                "America/Anchorage",
                "America/Sitka",
                "America/Metlakatla",
                "America/Yakutat",
                "America/Nome",
                "America/Adak"
        ));

        grouped.put("Hawaii", Arrays.asList(
                "Pacific/Honolulu"
        ));

        return grouped;
    }
}
