package com.open.spring.mvc.S3uploads;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
public class S3FileApiController {

    @Autowired
    private FileHandler fileHandler;

    /**
     * Upload a file to S3
     * @param uid User ID
     * @param filename Name of the file
     * @param base64Data Base64 encoded file content
     * @return Response with filename or error
     */
    @PostMapping("/upload/{uid}")
    public ResponseEntity<?> uploadFile(
            @PathVariable String uid,
            @RequestParam String filename,
            @RequestBody String base64Data) {
        
        if (com.open.spring.mvc.groups.DmNaming.reserved(uid)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Use the private conversation's attachment endpoint");
        }
        try {
            String result = fileHandler.uploadFile(base64Data, filename, uid);
            
            if (result != null) {
                Map<String, String> response = new HashMap<>();
                response.put("message", "File uploaded successfully");
                response.put("filename", result);
                response.put("uid", uid);
                return new ResponseEntity<>(response, HttpStatus.OK);
            } else {
                return new ResponseEntity<>("Upload failed", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("Error uploading file: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Download a file from S3
     * @param uid User ID
     * @param filename Name of the file to download
     * @return Base64 encoded file content
     */
    @GetMapping("/download/{uid}/{filename}")
    public ResponseEntity<?> downloadFile(
            @PathVariable String uid,
            @PathVariable String filename) {
        return downloadFileByQuery(uid, filename);
        }

        /**
         * Download a file from S3 using query parameters so nested keys survive URL parsing.
         * @param uid User ID
         * @param filename File key inside the user's S3 prefix
         * @return File bytes as a downloadable/inline response
         */
        @GetMapping("/download")
        public ResponseEntity<?> downloadFileByQuery(
            @RequestParam String uid,
            @RequestParam String filename) {
        
        if (com.open.spring.mvc.groups.DmNaming.reserved(uid)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Use the private conversation's attachment endpoint");
        }
        try {
            String base64Data = fileHandler.decodeFile(uid, filename);
            
            if (base64Data != null) {
                    byte[] data = java.util.Base64.getDecoder().decode(base64Data);
                    ByteArrayResource resource = new ByteArrayResource(data);

                    MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
                    org.springframework.util.MimeType mimeType = org.springframework.http.MediaTypeFactory.getMediaType(filename).orElse(null);
                    if (mimeType != null) {
                        mediaType = MediaType.parseMediaType(mimeType.toString());
                    }

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentDisposition(ContentDisposition.inline()
                            .filename(filename, StandardCharsets.UTF_8)
                            .build());

                    return ResponseEntity.ok()
                            .headers(headers)
                            .contentType(mediaType)
                            .contentLength(data.length)
                            .body(resource);
            } else {
                return new ResponseEntity<>("File not found", HttpStatus.NOT_FOUND);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("Error downloading file: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Delete all files for a user
     * @param uid User ID
     * @return Success or failure message
     */
    @DeleteMapping("/delete/{uid}")
    public ResponseEntity<?> deleteUserFiles(@PathVariable String uid) {
        if (com.open.spring.mvc.groups.DmNaming.reserved(uid)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Use the private conversation's attachment endpoint");
        }
        try {
            boolean result = fileHandler.deleteFiles(uid);
            
            if (result) {
                Map<String, String> response = new HashMap<>();
                response.put("message", "Files deleted successfully");
                response.put("uid", uid);
                return new ResponseEntity<>(response, HttpStatus.OK);
            } else {
                return new ResponseEntity<>("Delete failed", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("Error deleting files: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
