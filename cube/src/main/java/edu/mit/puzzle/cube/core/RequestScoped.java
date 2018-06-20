package edu.mit.puzzle.cube.core;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.inject.Qualifier;
import javax.inject.Scope;

@Documented
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Scope
public @interface RequestScoped {}