package com.finantrack.user.controller;

import com.finantrack.common.security.CurrentUser;
import com.finantrack.user.dto.ChangePasswordRequest;
import com.finantrack.user.dto.ConfirmEmailChangeRequest;
import com.finantrack.user.dto.DeletionRequest;
import com.finantrack.user.dto.EmailChangeRequest;
import com.finantrack.user.dto.PreferencesResponse;
import com.finantrack.user.dto.UpdatePreferencesRequest;
import com.finantrack.user.dto.UpdateProfileRequest;
import com.finantrack.user.dto.UserResponse;
import com.finantrack.user.service.AccountCredentialsService;
import com.finantrack.user.service.AccountDeletionService;
import com.finantrack.user.service.DataExportService;
import com.finantrack.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "Usuário")
public class UserController {

    private final UserService userService;
    private final AccountCredentialsService credentials;
    private final DataExportService dataExport;
    private final AccountDeletionService deletion;
    private final CurrentUser currentUser;

    public UserController(UserService userService, AccountCredentialsService credentials,
            DataExportService dataExport, AccountDeletionService deletion, CurrentUser currentUser) {
        this.userService = userService;
        this.credentials = credentials;
        this.dataExport = dataExport;
        this.deletion = deletion;
        this.currentUser = currentUser;
    }

    @GetMapping
    public UserResponse me() {
        return userService.getById(currentUser.id());
    }

    @PatchMapping
    public UserResponse rename(@Valid @RequestBody UpdateProfileRequest request) {
        return userService.rename(currentUser.id(), request.name());
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        credentials.changePassword(currentUser.id(), request.currentPassword(), request.newPassword());
    }

    @PostMapping("/email-change")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestEmailChange(@Valid @RequestBody EmailChangeRequest request) {
        credentials.requestEmailChange(currentUser.id(), request.newEmail(), request.currentPassword());
    }

    @PostMapping("/email-change/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmEmailChange(@Valid @RequestBody ConfirmEmailChangeRequest request) {
        credentials.confirmEmailChange(currentUser.id(), request.token());
    }

    @GetMapping("/preferences")
    public PreferencesResponse preferences() {
        return userService.getPreferences(currentUser.id());
    }

    @PatchMapping("/preferences")
    public PreferencesResponse updatePreferences(@Valid @RequestBody UpdatePreferencesRequest request) {
        return userService.updatePreferences(currentUser.id(), request);
    }

    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> export() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"finantrack-dados.json\"")
                .body(dataExport.export(currentUser.id()));
    }

    @PostMapping("/deletion-request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestDeletion(@Valid @RequestBody DeletionRequest request) {
        deletion.request(currentUser.id(), request.currentPassword());
    }
}
