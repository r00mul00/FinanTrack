package com.finantrack.auth.service;

import com.finantrack.category.service.CategoryService;
import com.finantrack.user.entity.User;
import com.finantrack.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final CategoryService categoryService;

    public UserRegistrationService(UserRepository userRepository, CategoryService categoryService) {
        this.userRepository = userRepository;
        this.categoryService = categoryService;
    }

    /** Cria o usuário e as categorias padrão em uma única transação. */
    @Transactional
    public User create(String name, String email, String passwordHash) {
        User user = userRepository.saveAndFlush(new User(name, email, passwordHash));
        categoryService.createDefaults(user.getId());
        return user;
    }
}
