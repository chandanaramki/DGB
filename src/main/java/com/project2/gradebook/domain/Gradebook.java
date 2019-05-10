package com.project2.gradebook.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName="gradebook")
public class Gradebook {
	private Integer id; 
	private String title;
	@JsonIgnore
	private StudentList studentList = new StudentList();
	
	public Gradebook() {
		super();
	}
	
	public Gradebook(int id, String title, StudentList studentList) {
		this.id = id;
		this.title = title;
		this.studentList = studentList;
	}
	
	public int getId() {
		return id;
	}
	public void setId(Integer id) {
		this.id = id;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public StudentList getStudentList() {
		return studentList;
	}
	public void setStudentList(StudentList studentList) {
		this.studentList = studentList;
	}

}
