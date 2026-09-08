package com.open.spring.mvc.groups;

import java.util.ArrayList;
import java.util.List;
import com.open.spring.mvc.person.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dm-preview")
@RequiredArgsConstructor
public class DmPreviewData implements CommandLineRunner {
    private final PersonJpaRepository people;
    private final PersonRoleJpaRepository roles;
    private final PasswordEncoder passwords;

    @Override
    public void run(String... args) {
        PersonRole role = roles.findByName("ROLE_USER");
        if (role == null) role = roles.save(new PersonRole("ROLE_USER"));
        int sid = 990001;
        for (String name : List.of("Alice", "Bob", "Charlie")) {
            String uid = "dm-" + name.toLowerCase(java.util.Locale.ROOT);
            if (people.findByUid(uid) == null) {
                Person person = new Person();
                person.setUid(uid);
                person.setName(name);
                person.setEmail(uid + "@example.invalid");
                person.setSid(String.valueOf(sid));
                person.setPassword(passwords.encode("DmPreview123!"));
                person.setRoles(new ArrayList<>(List.of(role)));
                people.save(person);
            }
            sid++;
        }
    }
}
