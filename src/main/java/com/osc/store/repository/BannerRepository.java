package com.osc.store.repository;

import com.osc.store.model.Banner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BannerRepository extends JpaRepository<Banner, Long> {

    List<Banner> findAllByOrderByPositionAscIdAsc();

    List<Banner> findByActiveTrueOrderByPositionAscIdAsc();
}
