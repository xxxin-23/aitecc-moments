package com.aitecc.moments.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 动态附件图片。库里存相对 URL（/uploads/xxx.jpg），换 CDN/对象存储时不用迁移数据。 */
@Entity
@Table(name = "post_images")
public class PostImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String url;

    /** 原始文件名，后台排错用 */
    @Column(name = "origin_name", length = 200)
    private String originName = "";

    @Column(name = "file_size")
    private Long fileSize = 0L;

    public PostImage() {}

    public PostImage(String url, String originName, Long fileSize) {
        this.url = url;
        this.originName = originName;
        this.fileSize = fileSize;
    }

    public Long getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public String getOriginName() {
        return originName;
    }

    public Long getFileSize() {
        return fileSize;
    }
}
