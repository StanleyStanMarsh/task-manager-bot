package ru.spbstu.hsai.usermanagement.exceptions;

public class SuperAdminDemoteException extends RuntimeException {
    public SuperAdminDemoteException() {
        super("Super Admin tries to self-demote");
    }
}
