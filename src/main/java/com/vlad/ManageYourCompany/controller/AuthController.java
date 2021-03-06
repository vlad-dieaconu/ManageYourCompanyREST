package com.vlad.ManageYourCompany.controller;

import com.vlad.ManageYourCompany.controller.payload.LoginRequest;
import com.vlad.ManageYourCompany.controller.payload.SignupRequest;
import com.vlad.ManageYourCompany.controller.payload.response.MessageResponse;
import com.vlad.ManageYourCompany.controller.payload.response.UserInfoResponse;
import com.vlad.ManageYourCompany.model.ERole;
import com.vlad.ManageYourCompany.model.PasswordResetToken;
import com.vlad.ManageYourCompany.model.Role;
import com.vlad.ManageYourCompany.model.User;
import com.vlad.ManageYourCompany.repositories.PasswordTokenRepository;
import com.vlad.ManageYourCompany.repositories.RoleRepository;
import com.vlad.ManageYourCompany.repositories.UserRepository;
import com.vlad.ManageYourCompany.security.UserDetailsImpl;
import com.vlad.ManageYourCompany.security.jwt.JwtUtils;
import com.vlad.ManageYourCompany.services.EmailServiceImpl;
import com.vlad.ManageYourCompany.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    PasswordTokenRepository passwordTokenRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtUtils jwtUtils;

    @Autowired
    EmailServiceImpl emailService;

    @Autowired
    UserService userService;


    private static final String signUpMessage = "Welcome to our team !" + "\n" + "Ask your admin the credentials for the login !";
    private static final String firstLoginMessage = "Because it's the first time you are logging in with the credentials that your admin set to you, please change your password" + "\n" +
            "Please access this link to change your password https://localhost:3000/changepassword";


    @PostMapping("/signin")
    ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {

        Authentication authentication = authenticationManager
                .authenticate(new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        ResponseCookie jwtCookie = jwtUtils.generateJwtCookie(userDetails);

        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());

        User user = userRepository.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User Not Found with username: " + loginRequest.getUsername()));
        if (user.isFirstLogin()) {
            emailService.sendMail(user.getEmail(), "Your first login to our app !", firstLoginMessage);
            user.setFirstLogin(false);
            userRepository.save(user);
        }

        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .body(new UserInfoResponse(userDetails.getId(),
                        userDetails.getUsername(),
                        userDetails.getEmail(),
                        roles));
    }

    //Because for the first time when a user login he will receive an email to change the password
    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {

        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Username is already taken!"));
        }

        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Email is already in use!"));
        }

        User user = new User(signUpRequest.getEmail(),
                signUpRequest.getUsername(),
                encoder.encode(signUpRequest.getPassword()));

        Set<String> strRoles = signUpRequest.getRole();
        Set<Role> roles = new HashSet<>();

        emailService.sendMail(signUpRequest.getEmail(), "Welcome to our company", signUpMessage);

        if (strRoles == null) {
            Role userRole = roleRepository.findByName(ERole.ROLE_USER)
                    .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
            roles.add(userRole);
        } else {
            strRoles.forEach(role -> {
                switch (role) {
                    case "admin":
                        Role adminRole = roleRepository.findByName(ERole.ROLE_ADMIN)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                        roles.add(adminRole);

                        break;
                    default:
                        Role userRole = roleRepository.findByName(ERole.ROLE_USER)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                        roles.add(userRole);
                }
            });
        }
        user.setFirstLogin(true);
        user.setRoles(roles);
        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }

    @PostMapping("/signout")
    public ResponseEntity<?> logoutUser() {
        ResponseCookie cookie = jwtUtils.getCleanJwtCookie();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new MessageResponse("You've been signed out!"));
    }

//    @PostMapping("/changepassword")
//    public ResponseEntity<?> changePassword(@RequestParam String email){
//
//        String changePasswordText = "Set your new password by clicking the link below" + "\n"
//                                    +"localhost:3000/changePassword";
//
//        User user = userRepository.findByEmail(email)
//                .orElseThrow(() -> new UsernameNotFoundException("User Not Found with email: " + email));
//        if(user != null){
//            emailService.sendMail(user.getEmail(),"Change your password", firstLoginMessage);
//            return ResponseEntity.ok("An email has been sent with all the details needed to change the password");
//        }
//
//        return ResponseEntity.badRequest().body(new MessageResponse("Email does not correspond to any account"));
//
//    }

    @PostMapping("/resetPassword")
    public ResponseEntity<?> resetPassword(@RequestParam("email") String userEmail) {
        User user = userRepository.findByEmail(userEmail).orElseThrow(() -> new UsernameNotFoundException("User Not Found with email: " + userEmail));

        if (user != null) {
            String token = UUID.randomUUID().toString();
            userService.createPasswordResetTokenForUser(user, token);

            String changePasswordText = "Set your new password by clicking the link below" + "\n"
                    + "http://localhost:3000/changePassword/" + token;

            emailService.sendMail(user.getEmail(), "Change your password", changePasswordText);
            return ResponseEntity.ok("An email has been sent with all the details needed to change the password");
        }
        return ResponseEntity.badRequest().body(new MessageResponse("Email does not correspond to any account"));
    }

    @GetMapping("/changePassword")
    public String showChangePasswordPage(@RequestParam("token") String token) {
        String result = userService.validatePasswordResetToken(token);
        if (result != null) {
            String failMessage = result;
            return failMessage;
        } else {
            String successMessage = "Valid token";
            return successMessage;
        }
    }

    @PutMapping("/savePassword")
    public ResponseEntity<?> savePassword(@RequestParam("token") String token, @RequestParam("password") String password) {

        PasswordResetToken passwordResetToken = passwordTokenRepository.findByToken(token);
        User user = passwordResetToken.getUser();
        user.setPassword(encoder.encode(password));
        userRepository.save(user);

        return ResponseEntity.ok("The password was changed!");
    }


}