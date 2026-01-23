package com.example.orthodox_prm;

import com.example.orthodox_prm.Enum.Role;
import com.example.orthodox_prm.model.User;
import com.example.orthodox_prm.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Controller for managing user whitelist and roles.
 * Only accessible by users with PRIEST role.
 */
@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('PRIEST')")
@Slf4j
public class UserManagementController {

    private final UserRepository userRepository;

    public UserManagementController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * List all whitelisted users
     */
    @GetMapping
    public String listUsers(Model model) {
        List<User> users = userRepository.findAllByOrderByRoleAscEmailAsc();
        model.addAttribute("users", users);
        model.addAttribute("roles", Role.values());
        return "admin/users";
    }

    /**
     * Add a new whitelisted user
     */
    @PostMapping("/add")
    public String addUser(@RequestParam String email,
                         @RequestParam String role,
                         @RequestParam(required = false) String displayName,
                         RedirectAttributes redirectAttributes) {

        // Validate email
        if (email == null || email.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Email address is required.");
            return "redirect:/admin/users";
        }

        email = email.trim().toLowerCase();

        // Check if email already exists
        if (userRepository.existsByEmailIgnoreCase(email)) {
            redirectAttributes.addFlashAttribute("error", "Email address is already whitelisted.");
            return "redirect:/admin/users";
        }

        // Validate role
        Role userRole;
        try {
            userRole = Role.valueOf(role);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "Invalid role selected.");
            return "redirect:/admin/users";
        }

        // Create user
        User user = new User();
        user.setEmail(email);
        user.setRole(userRole);
        user.setEnabled(true);
        if (displayName != null && !displayName.trim().isEmpty()) {
            user.setDisplayName(displayName.trim());
        }

        userRepository.save(user);
        log.info("New user whitelisted: {} with role {}", email, userRole);

        redirectAttributes.addFlashAttribute("success",
                "User " + email + " has been whitelisted with role " + userRole);
        return "redirect:/admin/users";
    }

    /**
     * Update a user's role
     */
    @PostMapping("/{id}/role")
    public String updateRole(@PathVariable Long id,
                            @RequestParam String role,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {

        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "User not found.");
            return "redirect:/admin/users";
        }

        // Prevent priests from demoting themselves
        String currentUserEmail = authentication.getName();
        if (user.getEmail().equalsIgnoreCase(currentUserEmail)) {
            redirectAttributes.addFlashAttribute("error",
                    "You cannot change your own role. Ask another priest to do this.");
            return "redirect:/admin/users";
        }

        // Validate role
        Role newRole;
        try {
            newRole = Role.valueOf(role);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "Invalid role selected.");
            return "redirect:/admin/users";
        }

        Role oldRole = user.getRole();
        user.setRole(newRole);
        userRepository.save(user);
        log.info("User {} role changed from {} to {}", user.getEmail(), oldRole, newRole);

        redirectAttributes.addFlashAttribute("success",
                "Role for " + user.getEmail() + " updated to " + newRole);
        return "redirect:/admin/users";
    }

    /**
     * Toggle a user's enabled status
     */
    @PostMapping("/{id}/toggle")
    public String toggleEnabled(@PathVariable Long id,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {

        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "User not found.");
            return "redirect:/admin/users";
        }

        // Prevent priests from disabling themselves
        String currentUserEmail = authentication.getName();
        if (user.getEmail().equalsIgnoreCase(currentUserEmail)) {
            redirectAttributes.addFlashAttribute("error",
                    "You cannot disable your own account.");
            return "redirect:/admin/users";
        }

        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
        log.info("User {} enabled status changed to {}", user.getEmail(), user.isEnabled());

        String action = user.isEnabled() ? "enabled" : "disabled";
        redirectAttributes.addFlashAttribute("success",
                "User " + user.getEmail() + " has been " + action);
        return "redirect:/admin/users";
    }

    /**
     * Remove a user from the whitelist
     */
    @PostMapping("/{id}/delete")
    public String deleteUser(@PathVariable Long id,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {

        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "User not found.");
            return "redirect:/admin/users";
        }

        // Prevent priests from deleting themselves
        String currentUserEmail = authentication.getName();
        if (user.getEmail().equalsIgnoreCase(currentUserEmail)) {
            redirectAttributes.addFlashAttribute("error",
                    "You cannot remove your own account from the whitelist.");
            return "redirect:/admin/users";
        }

        String email = user.getEmail();
        userRepository.delete(user);
        log.info("User {} removed from whitelist", email);

        redirectAttributes.addFlashAttribute("success",
                "User " + email + " has been removed from the whitelist");
        return "redirect:/admin/users";
    }
}
