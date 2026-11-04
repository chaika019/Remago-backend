package com.adrotation.crm.crm.entity;

import com.adrotation.crm.shared.entity.BaseEntity;
import com.adrotation.crm.user.entity.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "saved_views", uniqueConstraints = @UniqueConstraint(name = "uk_saved_view_user_module_name", columnNames = {"user_id", "module", "name"}))
public class SavedView extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 50)
    private String module;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(name = "filters_json", nullable = false, columnDefinition = "text")
    private String filtersJson;

    @Column(nullable = false)
    private boolean shared;
}
