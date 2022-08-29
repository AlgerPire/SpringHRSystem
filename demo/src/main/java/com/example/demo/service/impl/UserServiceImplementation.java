package com.example.demo.service.impl;

import com.example.demo.constants.Authority;
import com.example.demo.domain.User;
import com.example.demo.domain.UserPrincipal;
import com.example.demo.enumeration.Role;
import com.example.demo.exception.domain.EmailExistException;
import com.example.demo.exception.domain.UserNotFoundException;
import com.example.demo.exception.domain.UsernameExistException;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.UserService;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;


import java.sql.Date;
import java.util.EmptyStackException;
import java.util.List;

@Service
@Transactional
@Qualifier("userDetailsService")
public class UserServiceImplementation implements UserService, UserDetailsService {
    public static final String DEFAULT_IMAGE_PATH = "/user/image/profile/temp";
    private Logger LOGGER= LoggerFactory.getLogger(getClass());

    private UserRepository userRepository;
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    public UserServiceImplementation(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder=passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user= userRepository.findUserByUsername(username);
        if(user==null){
            LOGGER.error("User not found by username:"+username);
            throw new UsernameNotFoundException("User not found by username :"+username);
        }
        else{
            user.setLastLoginDateDisplay(user.getLastLoginDate());
            user.setLastLoginDate( new Date(System.currentTimeMillis()));
            userRepository.save(user);
            UserPrincipal userPrincipal=new UserPrincipal(user);
            LOGGER.info("Returning found user by username: "+username);
            return userPrincipal;
        }
    }


    //methods user service interface

    @Override
    public User register(String firstName, String lastName, String username, String email) throws UserNotFoundException, EmailExistException, UsernameExistException {
        validateNewUsernameAndEmail(StringUtils.EMPTY, username, email);
        User user=new User();
        user.setUserId(generateUserId());
        String password = generatePassword();
        String encodedPassword=encodePassword(password);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setUsername(username);
        user.setEmail(email);
        user.setJoinDate( new Date(System.currentTimeMillis()));
        user.setPassword(encodedPassword);
        user.setActive(true);
        user.setNotLocked(true);
        user.setAuthorities(Role.ROLE_USER.getAuthorities());
        user.setRole(Role.ROLE_USER.name());
        user.setProfileImageUrl(getTemporaryProfileImageUrl());
        userRepository.save(user);
        LOGGER.info("New user password "+password);
        return user;
    }

    private String encodePassword(String password) {
        return passwordEncoder.encode(password);
    }

    private String getTemporaryProfileImageUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(DEFAULT_IMAGE_PATH).toUriString();
    }

    private String generatePassword() {
        return RandomStringUtils.randomAlphanumeric(10);
    }

    private String generateUserId() {
        return RandomStringUtils.randomNumeric(10);
    }

    private User validateNewUsernameAndEmail(String currentUsername, String newUsername, String newEmail) throws UserNotFoundException, UsernameExistException, EmailExistException {
        User currentUser=findUserByUsername(currentUsername);
        User userByUsername=findUserByUsername(newUsername);
        User userByEmail=findUserByEmail(newEmail);

        if (StringUtils.isNotBlank(currentUsername)) {
            if(currentUser==null){
                throw new UserNotFoundException("No user found by username "+currentUsername);
            }
            if(userByUsername!=null && !currentUser.getId().equals(userByUsername.getId())){
                throw new UsernameExistException("Username you have entered already exists");
            }

            if(userByEmail!=null && !currentUser.getId().equals(userByEmail.getId())){
                throw new EmailExistException("Email already exists");
            }
            return currentUser;
        }
        else {
            if(userByUsername!=null){
                throw new UsernameExistException("Username already exists");
            }
            if(userByEmail!=null){
                throw new EmailExistException("Email already exists");
            }
            return null;
        }

    }

    @Override
    public List<User> getUsers() {

        return userRepository.findAll();
    }

    @Override
    public User findUserByUsername(String username) {

        return userRepository.findUserByUsername(username);
    }

    @Override
    public User findUserByEmail(String email) {

        return userRepository.findUserByEmail(email);
    }

}
