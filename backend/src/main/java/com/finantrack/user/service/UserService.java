package com.finantrack.user.service;

import com.finantrack.common.exception.ResourceNotFoundException;
import com.finantrack.user.dto.PreferencesResponse;
import com.finantrack.user.dto.UpdatePreferencesRequest;
import com.finantrack.user.dto.UserResponse;
import com.finantrack.user.entity.User;
import com.finantrack.user.mapper.UserMapper;
import com.finantrack.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        return userMapper.toResponse(load(id));
    }

    @Transactional
    public UserResponse rename(UUID id, String name) {
        User user = load(id);
        user.rename(name.trim());
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public PreferencesResponse getPreferences(UUID id) {
        return toPreferences(load(id));
    }

    @Transactional
    public PreferencesResponse updatePreferences(UUID id, UpdatePreferencesRequest request) {
        User user = load(id);
        if (request.currency() != null) {
            user.changeCurrency(request.currency());
        }
        if (request.theme() != null) {
            user.changeTheme(request.theme());
        }
        if (request.notifyBudget() != null) {
            user.changeNotifyBudget(request.notifyBudget());
        }
        if (request.notifyDueDates() != null) {
            user.changeNotifyDueDates(request.notifyDueDates());
        }
        return toPreferences(user);
    }

    private PreferencesResponse toPreferences(User user) {
        return new PreferencesResponse(user.getCurrency(), user.getTheme(), user.isNotifyBudget(), user.isNotifyDueDates());
    }

    private User load(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
    }
}
