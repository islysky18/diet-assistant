package com.chaoting.dietassistant.profile;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/profile")
    public String showProfile(Model model) {
        if (!model.containsAttribute("profileRequest")) {
            ProfileRequest request = profileService.getProfile()
                    .map(profileService::toRequest)
                    .orElseGet(ProfileRequest::new);
            model.addAttribute("profileRequest", request);
        }
        model.addAttribute("primaryGoals", PrimaryGoal.values());
        return "profile";
    }

    @PostMapping("/profile")
    public String saveProfile(
            @Valid @ModelAttribute("profileRequest") ProfileRequest profileRequest,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("primaryGoals", PrimaryGoal.values());
            return "profile";
        }

        profileService.save(profileRequest);
        redirectAttributes.addFlashAttribute("successMessage", "Profile saved.");
        return "redirect:/profile";
    }
}
