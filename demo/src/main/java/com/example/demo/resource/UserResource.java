package com.example.demo.resource;

import com.example.demo.domain.HttpResponse;
import com.example.demo.domain.User;
import com.example.demo.domain.UserPrincipal;
import com.example.demo.exception.domain.*;
import com.example.demo.service.UserService;
import com.example.demo.utility.JWTTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.mail.MessagingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static com.example.demo.constants.FileConstant.*;
import static com.example.demo.constants.SecurityConstant.JWT_TOKEN_HEADER;
import static org.springframework.http.MediaType.IMAGE_JPEG_VALUE;

@RestController
@RequestMapping({"/","/user"})
public class UserResource extends ExceptionHandling {

    private UserService userService;
    private AuthenticationManager authenticationManager;
    private JWTTokenProvider jwtTokenProvider;

    @Autowired
    public UserResource(UserService userService, AuthenticationManager authenticationManager, JWTTokenProvider jwtTokenProvider) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/register")
    public ResponseEntity<User> register(@RequestBody User user) throws UserNotFoundException, EmailExistException, UsernameExistException, MessagingException {
       User newUser= userService.register(user.getFirstName(),user.getLastName(),user.getUsername(),user.getEmail());
       return new ResponseEntity<>(newUser, HttpStatus.OK);
    }

    @PostMapping("/login")
    public ResponseEntity<User> login(@RequestBody User user)  {
        authenticate(user.getUsername(), user.getPassword());
        User loginUser = userService.findUserByUsername(user.getUsername());
        UserPrincipal userPrincipal = new UserPrincipal(loginUser);
        HttpHeaders jwtHeader = getJwtHeader(userPrincipal);
        return new ResponseEntity<>(loginUser, jwtHeader, HttpStatus.OK);
    }

    @PostMapping("/add")
    public ResponseEntity<User> addNewUser(@RequestParam("firstName") String firstName,
                                           @RequestParam("lastName") String lastName,
                                           @RequestParam("username") String username,
                                           @RequestParam("email") String email,
                                           @RequestParam("role") String role,
                                           @RequestParam("isActive") String isActive,
                                           @RequestParam("isNonLocked") String isNonLocked,
                                           @RequestParam(value = "profileImage", required = false) MultipartFile profileImage
                                           ) throws UserNotFoundException, EmailExistException, IOException, UsernameExistException {

        User newUser = userService.addNewUser(firstName,lastName, username, email, role, Boolean.parseBoolean(isActive),
                Boolean.parseBoolean(isNonLocked), profileImage);
        return new ResponseEntity<>(newUser, HttpStatus.OK);
    }

    @PostMapping("/update")
    public ResponseEntity<User> updateUser(@RequestParam("currentUsername") String currentUsername,
                                           @RequestParam("firstName") String firstName,
                                           @RequestParam("lastName") String lastName,
                                           @RequestParam("username") String username,
                                           @RequestParam("email") String email,
                                           @RequestParam("role") String role,
                                           @RequestParam("isActive") String isActive,
                                           @RequestParam("isNonLocked") String isNonLocked,
                                           @RequestParam(value = "profileImage", required = false) MultipartFile profileImage
    ) throws UserNotFoundException, EmailExistException, IOException, UsernameExistException {

        User updatedUser = userService.updateUser(currentUsername,firstName,lastName, username, email, role, Boolean.parseBoolean(isActive),
                Boolean.parseBoolean(isNonLocked), profileImage);
        return new ResponseEntity<>(updatedUser, HttpStatus.OK);
    }

    @GetMapping("/find/{username}")
    public ResponseEntity<User> getUser(@PathVariable("username") String username){
        User user = userService.findUserByUsername(username);
        return new ResponseEntity<>(user,HttpStatus.OK);
    }

    @GetMapping("/list")
    public ResponseEntity<List<User>> getAllUsers(){
        List<User> users = userService.getUsers();
        return new ResponseEntity<>(users,HttpStatus.OK);
    }

    @GetMapping("/resetPassword/{email}")
    public ResponseEntity<HttpResponse> resetPassword(@PathVariable("email") String email) throws EmailNotFoundException, MessagingException {
        userService.resetPassword(email);
        return response(HttpStatus.OK, "An email with a new password was sent to: "+email);
    }

    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAnyAuthority('user:delete')")
    public ResponseEntity<HttpResponse> deleteUser(@PathVariable("id") long id) {
        userService.deleteUser(id);
        return response(HttpStatus.NO_CONTENT, "User deleted successfully");
    }

    @PostMapping("/updateProfileImage")
    public ResponseEntity<User> updateProfileImage(@RequestParam("username") String username, @RequestParam(value = "profileImage") MultipartFile profileImage ) throws UserNotFoundException, EmailExistException, IOException, UsernameExistException {
        User user=userService.updateProfileImage(username,profileImage);
        return new ResponseEntity<>(user,HttpStatus.OK);
    }

    @GetMapping(path="/image/{username}/{fileName}", produces = IMAGE_JPEG_VALUE)
    public byte[] getProfileImage(@PathVariable("username") String username, @PathVariable("fileName") String fileName) throws IOException {
        return Files.readAllBytes(Paths.get(USER_FOLDER + username + FORWARD_SLASH + fileName));
    }

    @GetMapping(path = "/image/profile/{username}", produces = IMAGE_JPEG_VALUE)
    public byte[] getTempProfileImage(@PathVariable("username") String username) throws MalformedURLException {
        URL url = new URL(TEMP_PROFILE_IMAGE_BASE_URL+username);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try(InputStream inputStream = url.openStream()){
            int bytesRead;
            byte[] chunk = new byte[1024];
            while((bytesRead=inputStream.read(chunk))>0){
                byteArrayOutputStream.write(chunk,0,bytesRead);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return byteArrayOutputStream.toByteArray();
    }


    private ResponseEntity <HttpResponse> response(HttpStatus httpStatus, String message) {
        HttpResponse body = new HttpResponse(httpStatus.value(), httpStatus, httpStatus.getReasonPhrase().toUpperCase(), message.toUpperCase());
        return new ResponseEntity<>(body,httpStatus);

    }


    private HttpHeaders getJwtHeader(UserPrincipal user) {
        HttpHeaders headers=new HttpHeaders();
        headers.add(JWT_TOKEN_HEADER, jwtTokenProvider.generateJwtToken(user));
        return headers;
    }

    private void authenticate(String username, String password) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
    }
}
