package com.open.spring.linkpreview;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinkPreview {
    private String url;
    private String title;
    private String description;
    private String image;
    private String siteName;
}
