package com.finantrack.user.controller;

import com.finantrack.common.security.CurrentUser;
import com.finantrack.common.security.RateLimiter;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
    private final RateLimiter rateLimiter;

    public UserController(UserService userService, AccountCredentialsService credentials,
            DataExportService dataExport, AccountDeletionService deletion, CurrentUser currentUser,
            RateLimiter rateLimiter) {
        this.userService = userService;
        this.credentials = credentials;
        this.dataExport = dataExport;
        this.deletion = deletion;
        this.currentUser = currentUser;
        this.rateLimiter = rateLimiter;
    }

    @Operation(summary = "Dados do usuário autenticado")
    @GetMapping
    public UserResponse me() {
        return userService.getById(currentUser.id());
    }

    @Operation(summary = "Altera o nome")
    @PatchMapping
    public UserResponse rename(@Valid @RequestBody UpdateProfileRequest request) {
        return userService.rename(currentUser.id(), request.name());
    }

    @Operation(summary = "Troca a senha", description = "Exige a senha atual e encerra todas as sessões, inclusive a atual.")
    @ApiResponse(responseCode = "422", description = "Senha atual incorreta.")
    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        confirmingPassword();
        credentials.changePassword(currentUser.id(), request.currentPassword(), request.newPassword());
    }

    @Operation(summary = "Pede a troca de e-mail", description = "Envia um link de confirmação ao novo endereço; o e-mail só muda depois da confirmação. Um endereço já cadastrado não gera erro nem envio, para não revelar quais e-mails existem.")
    @ApiResponse(responseCode = "422", description = "Senha atual incorreta, ou o e-mail informado já é o da conta.")
    @PostMapping("/email-change")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestEmailChange(@Valid @RequestBody EmailChangeRequest request) {
        confirmingPassword();
        credentials.requestEmailChange(currentUser.id(), request.newEmail(), request.currentPassword());
    }

    @Operation(summary = "Confirma a troca de e-mail")
    @ApiResponse(responseCode = "409", description = "O e-mail deixou de estar disponível.")
    @ApiResponse(responseCode = "422", description = "Token inválido, expirado ou já usado.")
    @PostMapping("/email-change/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmEmailChange(@Valid @RequestBody ConfirmEmailChangeRequest request) {
        credentials.confirmEmailChange(currentUser.id(), request.token());
    }

    @Operation(summary = "Preferências", description = "Moeda, tema e notificações.")
    @GetMapping("/preferences")
    public PreferencesResponse preferences() {
        return userService.getPreferences(currentUser.id());
    }

    @Operation(summary = "Altera as preferências", description = "Só os campos enviados mudam.")
    @PatchMapping("/preferences")
    public PreferencesResponse updatePreferences(@Valid @RequestBody UpdatePreferencesRequest request) {
        return userService.updatePreferences(currentUser.id(), request);
    }

    @Operation(summary = "Exporta os dados pessoais", description = "Arquivo JSON com todos os dados do usuário (LGPD).")
    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> export() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"finantrack-dados.json\"")
                .body(dataExport.export(currentUser.id()));
    }

    @Operation(summary = "Pede a exclusão da conta", description = "Encerra todas as sessões. Os dados são apagados após 30 dias de carência; entrar na conta nesse prazo cancela o pedido.")
    @ApiResponse(responseCode = "422", description = "Senha atual incorreta.")
    @PostMapping("/deletion-request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestDeletion(@Valid @RequestBody DeletionRequest request) {
        confirmingPassword();
        deletion.request(currentUser.id(), request.currentPassword());
    }

    /**
     * As três rotas que conferem a senha atual compartilham o mesmo limite:
     * sem ele, um token roubado permitiria tentar senhas à vontade.
     */
    private void confirmingPassword() {
        rateLimiter.checkAccount("confirm-password", currentUser.id());
    }
}
