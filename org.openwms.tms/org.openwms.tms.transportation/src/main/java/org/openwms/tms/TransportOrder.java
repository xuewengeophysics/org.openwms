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
package org.openwms.tms;

import static org.openwms.tms.TransportOrderState.CANCELED;
import static org.openwms.tms.TransportOrderState.INITIALIZED;
import static org.openwms.tms.TransportOrderState.ONFAILURE;
import static org.openwms.tms.TransportOrderState.STARTED;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;
import javax.validation.constraints.Min;
import java.io.Serializable;
import java.util.Date;

import org.ameba.i18n.Translator;
import org.ameba.integration.jpa.ApplicationEntity;
import org.openwms.tms.exception.StateChangeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

/**
 * A TransportOrder is used to move {@code TransportUnit}s from a current {@code Location} to a target.
 *
 * @author <a href="mailto:scherrer@openwms.org">Heiko Scherrer</a>
 * @since 1.0
 */
@Configurable
@Entity
@Table(name = "TMS_TRANSPORT_ORDER")
public class TransportOrder extends ApplicationEntity implements Serializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransportOrder.class);

    /**
     * The bk of the {@code TransportUnit} to be moved by this {@code TransportOrder}. Allowed to be {@literal null} to keep {@code
     * TransportOrder}s without {@code TransportUnit}s.
     */
    @Column(name = "C_TRANSPORT_UNIT_BK")
    @Min(value = 1, groups = ChangeTU.class)
    private String transportUnitBK;

    /**
     * A priority level of the {@code TransportOrder}. The lower the value the lower the priority.<br> The priority level affects the
     * execution of the {@code TransportOrder}. An order with high priority will be processed faster than those with lower priority.
     */
    @Column(name = "C_PRIORITY")
    @Enumerated(EnumType.STRING)
    private PriorityLevel priority = PriorityLevel.NORMAL;

    /**
     * Date when the {@code TransportOrder} was started.
     */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "C_START_DATE")
    private Date startDate;

    /**
     * Last reported problem on the {@code TransportOrder}.
     */
    @Column(name = "C_PROBLEM")
    private Message problem;

    /**
     * Date when the {@code TransportOrder} ended.
     */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "C_END_DATE")
    private Date endDate;

    /**
     * State of the {@code TransportOrder}.
     */
    @Column(name = "C_STATE")
    @Enumerated(EnumType.STRING)
    private TransportOrderState state = TransportOrderState.CREATED;

    /**
     * The source {@code Location} of the {@code TransportOrder}.<br> This property is set before the {@code TransportOrder} was started.
     */
    @Column(name = "C_SOURCE_LOCATION")
    private String sourceLocation;

    /**
     * The target {@code Location} of the {@code TransportOrder}.<br> This property is set before the {@code TransportOrder} was started.
     */
    @Column(name = "C_TARGET_LOCATION")
    private String targetLocation;

    /**
     * A {@code LocationGroup} can also be set as target. At least one target must be set when the {@code TransportOrder} is being started.
     */
    @Column(name = "C_TARGET_LOCATION_GROUP")
    @Min(value = 1, groups = ChangeTU.class)
    private String targetLocationGroup;

    @Transient
    @Autowired
    private Translator translator;

    @Transient
    @Autowired
    private TransportOrderRepository repo;

    /* ----------------------------- constructors ------------------- */
    /** Dear JPA and Dozer ... */
    protected TransportOrder() {
    }

    /**
     * Create a TransportOrder with the given TransportUnit's business key.
     *
     * @param transportUnitBK TransportUnit business key
     */
    public TransportOrder(String transportUnitBK) {
        this.transportUnitBK = transportUnitBK;
    }

    /*~ ----------------------------- methods ------------------- */

    /**
     * Returns the priority level of the {@code TransportOrder}.
     *
     * @return The priority
     */
    public PriorityLevel getPriority() {
        return this.priority;
    }

    /**
     * Set the priority level of the {@code TransportOrder}.
     *
     * @param priority The priority to set
     */
    public void setPriority(PriorityLevel priority) {
        this.priority = priority;
    }

    /**
     * Returns the date when the {@code TransportOrder} was started.
     *
     * @return The date when started
     */
    public Date getStartDate() {
        return this.startDate;
    }

    /**
     * Get the {@code TransportUnit} assigned to the {@code TransportOrder} .
     *
     * @return The business key of the assigned {@code TransportUnit}
     */
    public String getTransportUnitBK() {
        return this.transportUnitBK;
    }

    /**
     * Assign a {@code TransportUnit} to the {@code TransportOrder}. Setting the {@code TransportUnit} to {@literal null} is allowed here to
     * unlink both.
     *
     * @param transportUnitBK The business key of the {@code TransportUnit} to be assigned
     */
    public void setTransportUnitBK(String transportUnitBK) {
        this.transportUnitBK = transportUnitBK;
    }

    /**
     * Returns the state of the {@code TransportOrder}.
     *
     * @return The state of the order
     */
    public TransportOrderState getState() {
        return state;
    }

    private void validateInitializationCondition() {
        if (transportUnitBK == null || transportUnitBK.isEmpty() || (targetLocation == null && targetLocationGroup == null)) {
            throw new StateChangeException(String.format("Not all properties set to turn TransportOrder into next state! transportUnit's barcode [%s], targetLocation [%s], targetLocationGroup [%s]", transportUnitBK, targetLocation, targetLocationGroup));
        }
    }

    /**
     * Validate whether a state change is valid or not. States must be changed in a defined order. Mostly the order is defined by the
     * ordering if the states in {@link TransportOrderState} enum class. But some other rules are checked here too and an exception is
     * thrown in case the sequence of states is violated.
     *
     * @param newState The new state of the order
     * @throws StateChangeException when <li>newState is {@literal null} or</li><li>the state shall be turned back to a prior state
     * or</li><li>when the caller tries to leap the state {@link TransportOrderState#INITIALIZED}</li>
     */
    private void validateStateChange(TransportOrderState newState) throws StateChangeException {
        LOGGER.debug("> Request for state change of TransportOrder with PK [{}] from [{}] to [{}]", getPk(), state, newState);

        if (newState == null) {
            throw new StateChangeException(translator.translate(TMSMessageCodes.TO_STATE_CHANGE_NULL_STATE), TMSMessageCodes.TO_STATE_CHANGE_NULL_STATE, getPersistentKey());
        }
        if (state.compareTo(newState) > 0) {
            // Don't allow to turn back the state!
            throw new StateChangeException(translator.translate(TMSMessageCodes.TO_STATE_CHANGE_BACKWARDS_NOT_ALLOWED, getPersistentKey()), TMSMessageCodes.TO_STATE_CHANGE_BACKWARDS_NOT_ALLOWED, getPersistentKey());
        }
        switch (state) {
            case CREATED:
                if (newState != INITIALIZED && newState != CANCELED) {
                    throw new StateChangeException(translator.translate(TMSMessageCodes.TO_STATE_CHANGE_NOT_READY, newState, getPersistentKey()), TMSMessageCodes.TO_STATE_CHANGE_NOT_READY, newState, getPersistentKey());
                }
                validateInitializationCondition();
                break;
            case INITIALIZED:
                if (newState != STARTED && newState != CANCELED && newState != ONFAILURE) {
                    throw new StateChangeException(translator.translate(TMSMessageCodes.STATE_CHANGE_ERROR_FOR_INITIALIZED_TO, getPersistentKey()), TMSMessageCodes.STATE_CHANGE_ERROR_FOR_INITIALIZED_TO, getPersistentKey());
                }
                if (newState == STARTED && numberOfStartedTOExists() > 0) {
                    throw new StateChangeException(translator.translate(TMSMessageCodes.START_TO_NOT_ALLOWED_ALREADY_STARTED_ONE, transportUnitBK, getPersistentKey()), TMSMessageCodes.START_TO_NOT_ALLOWED_ALREADY_STARTED_ONE, transportUnitBK, getPersistentKey());
                }
                LOGGER.debug("Current State is [{}], new state is [{}], #Started is [{}]", state, newState, repo.numberOfTransportOrders(transportUnitBK, STARTED));
                break;
            case STARTED:
                // new state may be one of the following, no additional if-check required here
                break;
            case FINISHED:
            case ONFAILURE:
            case CANCELED:
                throw new StateChangeException("Not allowed to change the state of a TransportOrder that has already been completed. Current state is CANCELED");
            default:
                throw new IllegalStateException("State not managed: " + state);
        }
        LOGGER.debug("< Request processed, order is now "+newState);
    }

    private int numberOfStartedTOExists() {
        return repo.numberOfTransportOrders(transportUnitBK, STARTED);
    }

    /**
     * Change the state of the {@code TransportOrder} regarding some rules.
     *
     * @param newState The new state of the order
     * @throws StateChangeException in case <ul> <li>the newState is {@literal null} or</li> <li>the newState is less than the old state
     * or</li> <li>the {@code TransportOrder} is in state {@link TransportOrderState#CREATED} and shall be manually turned into something
     * else then {@link TransportOrderState#INITIALIZED} or {@link TransportOrderState#CANCELED}</li> <li>the {@code TransportOrder} is
     * {@link TransportOrderState#CREATED} and shall be {@link TransportOrderState#INITIALIZED} but it is incomplete</li> </ul>
     */
    public void setState(TransportOrderState newState) throws StateChangeException {
        validateStateChange(newState);
        switch (newState) {
            case STARTED:
                startDate = new Date();
                break;
            case FINISHED:
            case ONFAILURE:
            case CANCELED:
                endDate = new Date();
                break;
            default:
                // OK for all others
        }
        state = newState;
        // TODO [openwms]: 24/07/16 publish state changed here!
    }

    /**
     * Get the target {@code Location} of this {@code TransportOrder}.
     *
     * @return The targetLocation if any, otherwise {@literal null}
     */
    public String getTargetLocation() {
        return targetLocation;
    }

    /**
     * Set the target {@code Location} of this {@code TransportOrder}.
     *
     * @param targetLocation The location to move on
     * @return this
     */
    public TransportOrder setTargetLocation(String targetLocation) {
        this.targetLocation = targetLocation;
        return this;
    }

    /**
     * Get the targetLocationGroup.
     *
     * @return The targetLocationGroup if any, otherwise {@literal null}
     */
    public String getTargetLocationGroup() {
        return targetLocationGroup;
    }

    /**
     * Set the targetLocationGroup.
     *
     * @param targetLocationGroup The targetLocationGroup to set.
     * @return this
     */
    public TransportOrder setTargetLocationGroup(String targetLocationGroup) {
        this.targetLocationGroup = targetLocationGroup;
        return this;
    }

    /**
     * Get the last {@link Message}.
     *
     * @return The last problem.
     */
    public Message getProblem() {
        return problem;
    }

    /**
     * Set the last {@link Message}.
     *
     * @param problem The {@link Message} to set.
     * @return this
     */
    public TransportOrder setProblem(Message problem) {
        this.problem = problem;
        return this;
    }

    /**
     * Get the endDate.
     *
     * @return The date the order ended
     */
    public Date getEndDate() {
        return endDate;
    }

    /**
     * Get the sourceLocation.
     *
     * @return The sourceLocation
     */
    public String getSourceLocation() {
        return sourceLocation;
    }

    /**
     * Set the sourceLocation.
     *
     * @param sourceLocation The sourceLocation to set
     * @return this
     */
    public TransportOrder setSourceLocation(String sourceLocation) {
        this.sourceLocation = sourceLocation;
        return this;
    }

    /**
     * Check whether a problem was reported on this TO.
     *
     * @return {@literal true} if so, otherwise {@literal false}
     */
    boolean hasProblem() {
        return problem != null;
    }

    /**
     * Check whether one of the targets has changed between this TransportOrder and the one passed as {@code transportOrder}.
     *
     * @param transportOrder The TransportOrder to verify against
     * @return {@literal true} if targets has changed, otherwise {@literal false}
     */
    boolean hasTargetChanged(TransportOrder transportOrder) {
        return ((targetLocation != null && !targetLocation.equals(transportOrder.getTargetLocation())) ||
                (targetLocationGroup != null && targetLocationGroup.equals(transportOrder.getTargetLocationGroup())));
    }
}
