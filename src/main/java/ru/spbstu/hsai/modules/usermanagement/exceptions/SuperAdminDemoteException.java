package ru.spbstu.hsai.modules.usermanagement.exceptions;

public class SuperAdminDemoteException extends RuntimeException {
    public SuperAdminDemoteException() {
        super("Super Admin tries to self-demote");
    }
}
