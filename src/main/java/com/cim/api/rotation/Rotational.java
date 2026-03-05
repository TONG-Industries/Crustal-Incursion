package com.cim.api.rotation;

public interface Rotational {
    long getSpeed();
    long getTorque();
    void setSpeed(long speed);
    void setTorque(long torque);
    long getMaxSpeed();    // для мотора – константа, для вала – не используется
    long getMaxTorque();   // аналогично
}