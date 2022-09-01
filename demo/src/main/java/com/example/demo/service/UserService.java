package com.example.demo.service;

import com.example.demo.domain.User;
import com.example.demo.exception.domain.EmailExistException;
import com.example.demo.exception.domain.UserNotFoundException;
import com.example.demo.exception.domain.UsernameExistException;

import javax.mail.MessagingException;
import java.util.List;

public interface UserService {

    User register(String firstName, String lastName, String username, String email) throws UserNotFoundException, EmailExistException, UsernameExistException, MessagingException;
    List<User> getUsers();
    User findUserByUsername(String username);
    User findUserByEmail(String email);
}
