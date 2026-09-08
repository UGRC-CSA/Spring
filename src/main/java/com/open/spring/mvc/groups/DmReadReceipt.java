package com.open.spring.mvc.groups;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "dm_read_receipt")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DmReadReceipt {
    @Id
    private String id; // groupId:personId
    private String readAt;
}
