package com.example.demo.service.impl;

import com.example.demo.constants.Authority;
import com.example.demo.constants.FileConstant;
import com.example.demo.constants.SecurityConstant;
import com.example.demo.domain.User;
import com.example.demo.domain.UserPrincipal;
import com.example.demo.enumeration.Role;
import com.example.demo.exception.domain.EmailExistException;
import com.example.demo.exception.domain.EmailNotFoundException;
import com.example.demo.exception.domain.UserNotFoundException;
import com.example.demo.exception.domain.UsernameExistException;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.EmailService;
import com.example.demo.service.LoginAttemptService;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;


import javax.mail.MessagingException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Date;
import java.util.EmptyStackException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static com.example.demo.constants.FileConstant.*;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@Service
@Transactional
@Qualifier("userDetailsService")
public class UserServiceImplementation implements UserService, UserDetailsService {

    private static final String NO_USER_FOUND_BY_EMAIL = "No user found for email: ";
    private Logger LOGGER= LoggerFactory.getLogger(getClass());

    private UserRepository userRepository;
    private BCryptPasswordEncoder passwordEncoder;
    private LoginAttemptService loginAttemptService;
    private EmailService emailService;

    @Autowired
    public UserServiceImplementation(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder, LoginAttemptService loginAttemptService, EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder=passwordEncoder;
        this.loginAttemptService=loginAttemptService;
        this.emailService=emailService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user= userRepository.findUserByUsername(username);
        if(user==null){
            LOGGER.error("User not found by username:"+username);
            throw new UsernameNotFoundException("User not found by username :"+username);
        }
        else{
            validateLoginAttempt(user);
            user.setLastLoginDateDisplay(user.getLastLoginDate());
            user.setLastLoginDate( new Date(System.currentTimeMillis()));
            userRepository.save(user);
            UserPrincipal userPrincipal=new UserPrincipal(user);
            LOGGER.info("Returning found user by username: "+username);
            return userPrincipal;
        }
    }

    private void validateLoginAttempt(User user)  {
        if(user.isNotLocked()){
            if(loginAttemptService.hasExceededMaxAttempts(user.getUsername())){
                user.setNotLocked(false);
            }
            else {
                user.setNotLocked(true);
            }
        }
        else {
            loginAttemptService.evictUserFromLoginAttemptCache(user.getUsername());
        }
    }


    //methods user service interface

    @Override
    public User register(String firstName, String lastName, String username, String email) throws UserNotFoundException, EmailExistException, UsernameExistException, MessagingException {
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
        user.setProfileImageUrl(getTemporaryProfileImageUrl(username));
        userRepository.save(user);
        emailService.sendNewPasswordEmail(firstName,password,email);
        return user;
    }

    private String encodePassword(String password) {
        return passwordEncoder.encode(password);
    }

    private String getTemporaryProfileImageUrl(String username) {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(DEFAULT_USER_IMAGE_PATH + username).toUriString();
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

    @Override
    public User addNewUser(String firstName, String lastName, String username, String email, String role, boolean isNonLocked, boolean isActive, MultipartFile profileImage) throws UserNotFoundException, EmailExistException, UsernameExistException, IOException {
        validateNewUsernameAndEmail(StringUtils.EMPTY, username, email);
        User user = new User();
        String password = generatePassword();
        String encodedPassword = encodePassword(password);
        user.setUserId(generateUserId());
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setJoinDate(new Date(System.currentTimeMillis()));
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(encodedPassword);
        user.setActive(isActive);
        user.setNotLocked(isNonLocked);
        user.setRole(getRoleEnumName(role).name());
        user.setAuthorities(getRoleEnumName(role).getAuthorities());
        user.setProfileImageUrl(getTemporaryProfileImageUrl(username));
        userRepository.save(user);
        saveProfileImage(user,profileImage);
        return user;
        
    }

    private void saveProfileImage(User user, MultipartFile profileImage) throws IOException {
        if (profileImage != null){
            Path userFolder = Paths.get(USER_FOLDER + user.getUsername()).toAbsolutePath().normalize();
            if(!Files.exists(userFolder)){
                Files.createDirectories(userFolder);
                LOGGER.info(DIRECTORY_CREATED + userFolder);
            }
            Files.deleteIfExists(Paths.get(userFolder + user.getUsername() + DOT + JPG_EXTENSION));
            Files.copy(profileImage.getInputStream(), userFolder.resolve(user.getUsername() + DOT + JPG_EXTENSION), REPLACE_EXISTING);
            user.setProfileImageUrl(setProfileImageUrl(user.getUsername()));
            userRepository.save(user);
            LOGGER.info(FILE_SAVED_IN_FILE_SYSTEM + profileImage.getOriginalFilename());
        }
    }

    private String setProfileImageUrl(String username) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(USER_IMAGE_PATH + username + FORWARD_SLASH + username + DOT + JPG_EXTENSION).toUriString();
    }

    private Role getRoleEnumName(String role) {
        return Role.valueOf(role.toUpperCase());
    }

    @Override
    public User updateUser(String currentUsername, String newFirstName, String newLastName, String newUsername, String newEmail, String role, boolean isNonLocked, boolean isActive, MultipartFile profileImage) throws UserNotFoundException, EmailExistException, UsernameExistException, IOException {
        User currentUser=validateNewUsernameAndEmail(currentUsername,newUsername,newEmail);
        currentUser.setFirstName(newFirstName);
        currentUser.setLastName(newLastName);
        currentUser.setEmail(newEmail);
        currentUser.setUsername(newUsername);
        currentUser.setActive(isActive);
        currentUser.setNotLocked(isNonLocked);
        currentUser.setRole(getRoleEnumName(role).name());
        currentUser.setAuthorities(getRoleEnumName(role).getAuthorities());
        userRepository.save(currentUser);
        saveProfileImage(currentUser,profileImage);
        return currentUser;
    }

    @Override
    public void deleteUser(long id) {
        userRepository.deleteById(id);

    }

    @Override
    public void resetPassword(String email) throws EmailNotFoundException, MessagingException {
        User user = userRepository.findUserByEmail(email);
        if(user == null){
            throw new EmailNotFoundException(NO_USER_FOUND_BY_EMAIL + email);
        }
        String password = generatePassword();
        user.setPassword(encodePassword(password));
        userRepository.save(user);
        emailService.sendNewPasswordEmail(user.getFirstName(),password,user.getEmail() );

    }

    @Override
    public User updateProfileImage(String username, MultipartFile profileImage) throws UserNotFoundException, EmailExistException, UsernameExistException, IOException {
        User user = validateNewUsernameAndEmail(username,null,null);
        saveProfileImage(user,profileImage);
        return user;
    }

}
