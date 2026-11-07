package com.osc.store.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "banners")
@Getter
@Setter
@NoArgsConstructor
public class Banner {

    public static final int MAX_BANNERS = 4;
    public static final String DEFAULT_COLOR = "#D71920";
    public static final String DEFAULT_STYLE = "solid";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(length = 2000)
    private String description;

    @Column(length = 20)
    private String backgroundColor = DEFAULT_COLOR;

    @Column(length = 20)
    private String backgroundColor2;

    @Column(length = 32)
    private String backgroundStyle = "solid";

    @Column(length = 500)
    private String imagePath;

    private String ctaText;

    @Column(length = 500)
    private String linkUrl;

    private String ctaText2;

    @Column(length = 500)
    private String linkUrl2;

    private int position;

    private boolean active = true;
}
