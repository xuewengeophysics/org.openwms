/*
 * openwms.org, the Open Warehouse Management System.
 * Copyright (C) 2014 Heiko Scherrer
 *
 * This file is part of openwms.org.
 *
 * openwms.org is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * openwms.org is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software. If not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.openwms.wms;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.PostLoad;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.openwms.common.domain.TransportUnit;
import org.openwms.core.domain.AbstractEntity;
import org.openwms.core.exception.DomainModelRuntimeException;
import org.openwms.wms.inventory.Product;

/**
 * A LoadUnit is used to divide a {@link TransportUnit} into physical areas. It
 * is used for separation concerns only and cannot be transported without a
 * {@link TransportUnit}.
 * 
 * @author <a href="mailto:scherrer@openwms.org">Heiko Scherrer</a>
 * @version $Revision: $
 * @since 0.1
 * @see org.openwms.common.domain.TransportUnit
 */
@Entity
@Table(name = "WMS_LOAD_UNIT", uniqueConstraints = @UniqueConstraint(columnNames = { "C_TRANSPORT_UNIT",
        "C_PHYSICAL_POS" }))
@NamedQueries({
        @NamedQuery(name = LoadUnit.NQ_FIND_ALL, query = "select lu from LoadUnit lu"),
        @NamedQuery(name = LoadUnit.NQ_FIND_WITH_BARCODE, query = "select lu from LoadUnit lu where lu.transportUnit.barcode = :"
                + LoadUnit.QP_FIND_WITH_BARCODE_BARCODE + " order by lu.physicalPosition") })
public class LoadUnit extends AbstractEntity<Long> implements Serializable {

    private static final long serialVersionUID = -5524006837325285793L;

    /** Unique technical key. */
    @Id
    @Column(name = "C_ID")
    @GeneratedValue
    private Long id;

    /** The {@link TransportUnit} where this {@link LoadUnit} belongs to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "C_TRANSPORT_UNIT")
    private TransportUnit transportUnit;

    /** Where this {@link LoadUnit} is located on the {@link TransportUnit}. */
    @Column(name = "C_PHYSICAL_POS")
    private String physicalPosition;

    /** Locked for allocation. */
    @Column(name = "C_LOCKED")
    private boolean locked = false;

    /** The Product that is carried in this LoadUnit. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "C_PRODUCT_ID", referencedColumnName = "C_SKU")
    private Product product;

    /** The date this LoadUnit was created. */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "C_CREATED_DT")
    private Date createdDate;

    /** The date this LoadUnit has changed the last time. */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "C_CHANGED_DT")
    private Date changedDate;

    /** Version field. */
    @Version
    @Column(name = "C_VERSION")
    private long version;

    /** All {@link PackagingUnit}s that belong to this LoadUnit. */
    @OneToMany(mappedBy = "loadUnit")
    private Set<PackagingUnit> packagingUnits = new HashSet<PackagingUnit>();

    /**
     * Query to find all <code>LoadUnit</code>s.<br />
     * Query name is {@value} .
     */
    public static final String NQ_FIND_ALL = "LoadUnit.findAll";

    /**
     * Query to find all <code>LoadUnit</code>s that belong to a
     * <code>TransportUnit</code>. <li>
     * Query parameter name <strong>{@value #QP_FIND_WITH_BARCODE_BARCODE}
     * </strong> : The barcode of the <code>TransportUnit</code> to search for.</li>
     * <br />
     * Query name is {@value} .
     */
    public static final String NQ_FIND_WITH_BARCODE = "LoadUnit.findWithBarcode";
    public static final String QP_FIND_WITH_BARCODE_BARCODE = "barcode";

    /**
     * Accessed by persistence provider.
     */
    protected LoadUnit() {}

    /**
     * Create a new LoadUnit.
     * 
     * @param tu
     *            The {@link TransportUnit} where this LoadUnit stands on.
     * @param physicalPosition
     *            The physical position within the {@link TransportUnit} where
     *            this LoadUnit stands on
     */
    public LoadUnit(TransportUnit tu, String physicalPosition) {
        this.transportUnit = tu;
        this.physicalPosition = physicalPosition;
    }

    /**
     * Create a new LoadUnit.
     * 
     * @param tu
     *            The {@link TransportUnit} where this LoadUnit stands on.
     * @param physicalPosition
     *            The physical position within the {@link TransportUnit} where
     *            this LoadUnit stands on
     * @param product
     *            The {@link Product} to set on this LoadUnit
     */
    public LoadUnit(TransportUnit tu, String physicalPosition, Product product) {
        this(tu, physicalPosition);
        this.product = product;
    }

    /**
     * Set the creation date. Check that a TransportUnit is set. Notice, that we
     * throw a RuntimeException instead of a checked exception because these
     * check shall be done in the outer service layer already.
     */
    @PrePersist
    protected void prePersist() {
        if (null == this.transportUnit) {
            throw new DomainModelRuntimeException("Not allowed to create a new LoadUnit without a TransportUnit");
        }
        this.createdDate = new Date();
        this.changedDate = new Date();
    }

    /**
     * Set the changed date.
     */
    @PreUpdate
    protected void preUpdate() {
        this.changedDate = new Date();
    }

    @PostLoad
    protected void postLoad() {
        this.transportUnit.getVersion();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isNew() {
        return this.id == null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getVersion() {
        return this.version;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getId() {
        return id;
    }

    /**
     * Get the transportUnit.
     * 
     * @return the transportUnit.
     */
    public TransportUnit getTransportUnit() {
        return transportUnit;
    }

    /**
     * Get the physicalPosition.
     * 
     * @return the physicalPosition.
     */
    public String getPhysicalPosition() {
        return physicalPosition;
    }

    /**
     * Get the locked.
     * 
     * @return the locked.
     */
    public boolean isLocked() {
        return locked;
    }

    /**
     * Set the locked.
     * 
     * @param locked
     *            The locked to set.
     */
    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    /**
     * Get the product.
     * 
     * @return the product.
     */
    public Product getProduct() {
        return product;
    }

    /**
     * Set the product.
     * 
     * @param product
     *            The product to set.
     */
    public void setProduct(Product product) {
        if (this.product != null) {
            throw new DomainModelRuntimeException("Not allowed to change the Product of an LoadUnit");
        }
        this.product = product;
    }

    /**
     * Unassign the product from this LoadUnit - set it to <code>null</code>.
     */
    public void unassignProduct() {
        this.product = null;
    }

    /**
     * Get the createdDate.
     * 
     * @return the createdDate.
     */
    public Date getCreatedDate() {
        return createdDate == null ? null : new Date(createdDate.getTime());
    }

    /**
     * Get the changedDate.
     * 
     * @return the changedDate.
     */
    public Date getChangedDate() {
        return changedDate == null ? null : new Date(changedDate.getTime());
    }

    /**
     * Get the packagingUnits.
     * 
     * @return the packagingUnits.
     */
    public Set<PackagingUnit> getPackagingUnits() {
        return packagingUnits;
    }

    /**
     * Add one or more {@link PackagingUnit}s to this LoadUnit.
     * 
     * @param pUnits
     *            {@link PackagingUnit}s to add
     * @return <code>true</code> if this set changed as a result of the call
     */
    public boolean addPackagingUnits(PackagingUnit... pUnits) {
        return this.packagingUnits.addAll(Arrays.asList(pUnits));
    }

    /**
     * Remove one or more {@link PackagingUnit}s from this LoadUnit.
     * 
     * @param pUnits
     *            {@link PackagingUnit}s to remove
     */
    public void removePackagingUnits(PackagingUnit... pUnits) {
        this.packagingUnits.removeAll(Arrays.asList(pUnits));
    }

    /**
     * {@inheritDoc}
     * 
     * Return a combination of the barcode and the physicalPosition.
     */
    @Override
    public String toString() {
        return this.transportUnit.getBarcode() + " / " + this.physicalPosition;
    }
}