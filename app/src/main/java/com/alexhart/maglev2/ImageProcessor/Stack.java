package com.alexhart.maglev2.ImageProcessor;

public class Stack{
    private Object currentObject;
    private int nObject;

    public Stack(){
        currentObject = null;
        nObject = 0;
    }

    public void push(Object newObject){
        newObject.setNextObject(currentObject);
        currentObject = newObject;
        nObject++;
    }

    public Object pop(){
        Object temp = currentObject;
        currentObject = currentObject.getNextObject();
        nObject--;
        return temp;
    }

    public boolean isEmpty(){
        return currentObject == null;
    }

    public int numOfObject(){
        return nObject;
    }
}