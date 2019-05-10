package com.project2.gradebook.controller;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import com.project2.gradebook.domain.Gradebook;
import com.project2.gradebook.domain.GradebookList;
import com.project2.gradebook.domain.Student;
import com.project2.gradebook.domain.StudentList;

@RestController
public class GradeBookController {

	static Map<Integer, Gradebook> primaryGradebookMap = new ConcurrentHashMap<Integer, Gradebook>();
	static Map<Integer, Gradebook> secondaryGradebookMap = new ConcurrentHashMap<Integer, Gradebook>();
	public static final String ORDINARY_USER = "ordinary";
	public static final String ADMIN_USER = "admin";
	public static final String HTTP = "http";
	public static final String LOCALHOST = "localhost:";
	public static int ID_SEQ = 0;

	@Autowired
	private RestTemplate restTemplate;
	private static String secondaryServer = System.getProperty("server.secondaryPort");
	private static String idSequence = System.getProperty("server.gradebookIdPort");

	@RequestMapping(value = "/gradebookid", method = RequestMethod.GET)
	synchronized public Integer gradeBookIdNextSeq() {
		return ID_SEQ = ID_SEQ + 1;
	}

	@RequestMapping(value = "/gradebook", method = RequestMethod.GET, produces = "text/xml")
	public ResponseEntity<?> getAllGradebooks(@RequestHeader(name = "userType", required = true) String userType) {
		if (userType != null && userType.equals(ORDINARY_USER)) {
			GradebookList gradebookListObj = new GradebookList();
			gradebookListObj.gradebookList = new ArrayList<Gradebook>();
			for (Integer id : primaryGradebookMap.keySet()) {
				Gradebook gradebook = primaryGradebookMap.get(id);
				gradebookListObj.gradebookList.add(gradebook);
			}
			GradebookList secondaryGradebook = this.getAllSecondaryCopy();
			if (secondaryGradebook != null & secondaryGradebook.gradebookList != null) {
				gradebookListObj.gradebookList.addAll(secondaryGradebook.gradebookList);
			}
			return new ResponseEntity<GradebookList>(gradebookListObj, HttpStatus.OK);
		} else {
			return new ResponseEntity<>("User not authorized", HttpStatus.UNAUTHORIZED);
		}
	}

	public GradebookList getAllSecondaryCopy() {
		GradebookList secondaryCopyObj = new GradebookList();
		secondaryCopyObj.gradebookList = new ArrayList<Gradebook>();
		for (Integer id : secondaryGradebookMap.keySet()) {
			Gradebook gradebook = secondaryGradebookMap.get(id);
			secondaryCopyObj.gradebookList.add(gradebook);
		}
		return secondaryCopyObj;
	}

	@RequestMapping(value = "/gradebook/{name}", method = { RequestMethod.PUT, RequestMethod.POST })
	public ResponseEntity<?> createGradeBook(@PathVariable("name") String name,
			@RequestHeader(name = "userType", required = true) String userType) {
		if (userType != null && userType.equals(ORDINARY_USER)) {
			StudentList studentList = new StudentList();
			if (!isValidTitle(name)) {
				return new ResponseEntity<>("Invalid/Bad Request", HttpStatus.BAD_REQUEST);
			} else if (containsGradeBookTitle(primaryGradebookMap, name)) {
				return new ResponseEntity<>("The title already exists.", HttpStatus.BAD_REQUEST);
			}
			String URI = HTTP + "://" + LOCALHOST + secondaryServer + "/gradebook";
			MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
			headers.add("userType", userType);
			HttpEntity<?> request = new HttpEntity<Object>(headers);
			ResponseEntity<GradebookList> entity = this.restTemplate.exchange(URI, HttpMethod.GET, request,
					GradebookList.class);
			GradebookList gradebooks = entity.getBody();
			if (gradebooks != null && containsGradeBookTitle(gradebooks, name)) {
				return new ResponseEntity<>("The title already exists.", HttpStatus.BAD_REQUEST);
			}
			// getting next value of gradebook id
			URI = HTTP + "://" + LOCALHOST + idSequence + "/gradebookid";
			ID_SEQ = this.restTemplate.getForObject(URI, Integer.class);

			Gradebook gb = new Gradebook(ID_SEQ, name, studentList);
			primaryGradebookMap.put(ID_SEQ, gb);
			return new ResponseEntity<>("Gradebook added and the id of new gradebook is " + ID_SEQ, HttpStatus.OK);
		} else {
			return new ResponseEntity<>("User is not Authorized", HttpStatus.UNAUTHORIZED);
		}
	}

	@RequestMapping(path = "/secondary/{id}", method = { RequestMethod.PUT, RequestMethod.POST })
	public ResponseEntity<String> createSecondary(@PathVariable Integer id,
			@RequestHeader(name = "userType", required = true) String userType) {
		if (userType != null && userType.equals(ADMIN_USER)) {
			Gradebook gradebook;
			if (primaryGradebookMap.keySet().contains(id)) {
				return new ResponseEntity<>("Server is the primary server for the given GradeBook Id",
						HttpStatus.NOT_ACCEPTABLE);
			}
			gradebook = getGradebookFromPrimary(id);
			if (gradebook == null) {
				return new ResponseEntity<>("There is no GradeBook with the given id", HttpStatus.NOT_FOUND);
			}
			// To check if the secondary copy is already present on the server.
			if (secondaryGradebookMap.keySet().contains(id)) {
				return new ResponseEntity<>("Secondary copy already exists for the given GradeBook Id",
						HttpStatus.NOT_ACCEPTABLE);
			}

			Gradebook secondaryCopy = new Gradebook();
			secondaryCopy.setTitle(gradebook.getTitle());
			secondaryCopy.setId(id);
			secondaryCopy.setStudentList(gradebook.getStudentList());
			secondaryGradebookMap.put(id, secondaryCopy);
			return new ResponseEntity<>("Secondary copy for the given GradeBook Id is created succesfully",
					HttpStatus.OK);
		} else {
			return new ResponseEntity<>("User is not Authorized", HttpStatus.UNAUTHORIZED);
		}

	}

	@RequestMapping(path = "/gradebook/{id}", method = RequestMethod.DELETE)
	public ResponseEntity<String> deleteGradebook(@PathVariable Integer id,
			@RequestHeader(name = "userType", required = true) String userType) {
		if (userType != null && userType.equals(ADMIN_USER)) {
			if (primaryGradebookMap.keySet().contains(id)) {
				deleteSecondaryCopy(id);
				primaryGradebookMap.remove(id);
				return new ResponseEntity<>(HttpStatus.OK);
			}
			return new ResponseEntity<>("There is no Primary GradeBook with the given id", HttpStatus.NOT_FOUND);
		} else {
			return new ResponseEntity<>("User is not Authorized", HttpStatus.UNAUTHORIZED);
		}

	}

	@RequestMapping(path = "/secondary/{id}", method = RequestMethod.DELETE)
	public ResponseEntity<String> removeSecondaryGradebook(@PathVariable Integer id,
			@RequestHeader(name = "userType", required = true) String userType) {
		if (userType != null && userType.equals(ADMIN_USER)) {
			if (secondaryGradebookMap.keySet().contains(id)) {
				secondaryGradebookMap.remove(id);
				return new ResponseEntity<>(HttpStatus.OK);
			}
			Gradebook gradebook = getGradebookFromPrimary(id);
			if (gradebook == null) {
				return new ResponseEntity<>("There is no GradeBook with the given id", HttpStatus.NOT_FOUND);
			} else {
				return new ResponseEntity<>("The server does not have a secondary copy of the GradeBook",
						HttpStatus.NOT_FOUND);
			}
		} else {
			return new ResponseEntity<>("User is not Authorized", HttpStatus.UNAUTHORIZED);
		}
	}

	
	@RequestMapping(value ="/gradebook/{id}/student/{name}/grade/{grade}", method = { RequestMethod.PUT, RequestMethod.POST })
	public ResponseEntity<String> createStudent(@PathVariable("id") int id,@PathVariable("name") String name,  @PathVariable("grade")  String grade) {
		Gradebook gradebook = primaryGradebookMap.get(id);
		boolean updatedGrade =false;
		if(gradebook == null) {
			return new ResponseEntity<>("There is no GradeBook with the given id",HttpStatus.NOT_FOUND);
		}
		StudentList gradebookStudents = gradebook.getStudentList();
		
		if(gradebookStudents == null ) {
			gradebookStudents = new StudentList();
		}
		if(gradebookStudents.studentList == null ) {
			gradebookStudents.studentList = new ArrayList<>();
		}
		for(Student s: gradebookStudents.studentList) {	
			//If a student with given name exists in the gradebook, just update the grade.
			if(s.getName().equals(name)) {
				s.setGrade(grade);
				updatedGrade = true;
		 	}
		}
			//If student is not present, add to the gradebook.
			if (!updatedGrade) {
				Student newStudent = new Student();
				newStudent.setGrade(grade);
				newStudent.setName(name);
				gradebookStudents.studentList.add(newStudent);
		 	}
			primaryGradebookMap.get(id).setStudentList(gradebookStudents);
			//Check if  gradebook is present in the secondary server(if Any)
		    Gradebook secondarygradebook = getSecondaryGradebook(id);
		    if(secondarygradebook!=null) {
		    	addStudentSecondaryCopy (id, name, grade);
		    }
			    
		return new ResponseEntity<>("Student Successfully updated/created for the given gradebook id",HttpStatus.OK);
	}
	
	   public void addStudentSecondaryCopy(Integer id, String name, String grade) {
			String url = HTTP + "://" + LOCALHOST + secondaryServer + "/secondarygradebook/" + id + "/student/" + name + "/grade/" + grade; 
			this.restTemplate.postForObject(url, name, String.class);
	   }
	   
	   @RequestMapping(value ="/secondarygradebook/{id}/student/{name}/grade/{grade}", method=RequestMethod.POST)
		public void addSecondaryStudent(@PathVariable("id") int id,@PathVariable("name") String name, @PathVariable("grade") String grade) {
			Gradebook secondaryGradebook = secondaryGradebookMap.get(id);
			boolean updatedGrade = false;
			if(secondaryGradebook!=null) {
				StudentList secondaryStudentList = secondaryGradebook.getStudentList();
				if(secondaryStudentList == null) {
					secondaryStudentList = new StudentList();
				}
				if(secondaryStudentList.studentList == null ) {
					secondaryStudentList.studentList = new ArrayList<>();
				}
				for(Student s: secondaryStudentList.studentList) {		
		    		 if(s.getName().equals(name)) {
							s.setGrade(grade);
							updatedGrade = true;
					 	}
				}
						//If student is not present, add to the gradebook.
						if(!updatedGrade) {
							Student studentCopy = new Student();
							studentCopy.setGrade(grade);
							studentCopy.setName(name);
							secondaryStudentList.studentList.add(studentCopy);

					 }
						secondaryGradebookMap.get(id).setStudentList(secondaryStudentList);
		    	 }
			}          
	   
	// Mapping to get all Students from within Gradebook

		@RequestMapping(value = "/gradebook/{id}/student", method = RequestMethod.GET, produces = "text/xml")
		public ResponseEntity<?> getAllStudent(@PathVariable("id") int id) {

			Gradebook gradebook = primaryGradebookMap.get(id);
			if (gradebook == null) {
				gradebook = secondaryGradebookMap.get(id);
				if (gradebook == null) {
					return new ResponseEntity<String>("No Gradebook for the ID", HttpStatus.NO_CONTENT);
				}
			}

			StudentList studentList = gradebook.getStudentList();
			if (studentList != null) {
				return new ResponseEntity<>(studentList, HttpStatus.OK);
			}
			return new ResponseEntity<String>("No Student Details for Gradebook", HttpStatus.NO_CONTENT);
		}
		
		
		@RequestMapping(value ="/gradebook/{id}/student/{name}", method=RequestMethod.DELETE)
		public ResponseEntity<String> deleteStudent(@PathVariable("id") int id,@PathVariable("name") String name) {
			Gradebook gradebook = primaryGradebookMap.get(id);
			if(gradebook == null) {
				return new ResponseEntity<>("There is no GradeBook with the given id",HttpStatus.NOT_FOUND);
			}
			StudentList gradebookStudents = gradebook.getStudentList();
			if(!gradebookStudents.studentList.isEmpty()) {
				//To check if there are any secondary Copy of students in Secondary Copy of gradebook(if Any)
				    Gradebook secondarygradebook = getSecondaryGradebook(id);
				    if(secondarygradebook!=null && secondarygradebook.getStudentList()!=null) {
				    	//to remove student record on secondaryCopy of secondary server if any
				    	deleteStudentSecondaryCopy (id, name);
				    }
				    //Deleting a student from Primary Copy of gradebook.
				    	 
				    for (Iterator<Student> it = gradebookStudents.studentList.iterator(); it.hasNext();) {
						Student student = it.next();
						if(student.getName().equals(name)) {
			 				it.remove();
			 				return new ResponseEntity<String>("Deleted student for the given gradebook id",HttpStatus.OK);
			 			}
					}
				}
			return new ResponseEntity<String>("No Student found ",HttpStatus.NOT_FOUND);
		}
	   
		  public void deleteStudentSecondaryCopy(Integer id, String name) {
				try {
					 URI url = new URI(HTTP + "://" + LOCALHOST + secondaryServer + "/secondarygradebook/" + id + "/student/" + name);
					 MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
					 headers.add("userType", "ordinary");
					 HttpEntity<?> request = new HttpEntity<Object>(headers);
					 this.restTemplate.exchange(url, HttpMethod.DELETE, request, String.class);
				}catch (URISyntaxException e) {
					e.printStackTrace();
				}
					
			   }
			   
			   @RequestMapping(value ="/secondarygradebook/{id}/student/{name}", method=RequestMethod.DELETE)
				public void removeSecondaryStudent(@PathVariable("id") int id,@PathVariable("name") String name) {
					Gradebook secondarygradebook = secondaryGradebookMap.get(id);
					if(secondarygradebook != null && secondarygradebook.getStudentList() != null && secondarygradebook.getStudentList().studentList!=null) {
						StudentList secondaryStudentList = secondarygradebook.getStudentList();
						for (Iterator<Student> it = secondaryStudentList.studentList.iterator(); it.hasNext();) {
							Student student = it.next();
							if(student.getName().equals(name)) {
				 				it.remove();
				 			}
						}
					    	 secondaryGradebookMap.get(id).setStudentList(secondaryStudentList);
					}
			   }
			

	@RequestMapping(value = "/gradebook/{id}/student/{name}", method = RequestMethod.GET)
	public ResponseEntity<?> getStudent(@PathVariable("id") int id, @PathVariable("name") String name) {

		Student student = null;
		StudentList gradebookStudents = new StudentList();
		Gradebook gradebook = primaryGradebookMap.get(id);
        if(gradebook == null) {
        	gradebook = secondaryGradebookMap.get(id);
		}
		if(gradebook == null) {
			return new ResponseEntity<>("There is no GradeBook with the given id",HttpStatus.NOT_FOUND);
		}
			gradebookStudents =gradebook.getStudentList();
		
		for (Student s : gradebookStudents.studentList) {
			if (s.getName().equals(name)) {
				student = new Student();
				student.setName(s.getName());
				student.setGrade(s.getGrade());
				break;
			}
		}
		if (student == null) {
			return new ResponseEntity<String>("Student not found for the given gradebook id", HttpStatus.NOT_FOUND);
		}
		return new ResponseEntity<>(student, HttpStatus.OK);
	}
	public Gradebook getGradebookFromPrimary(Integer id) {
		String uri = HTTP + "://" + LOCALHOST + secondaryServer + "/gradebook/" + id;
		Gradebook gradebook;
		gradebook = this.restTemplate.getForObject(uri, Gradebook.class);
		return gradebook;
	}

	public void deleteSecondaryCopy(Integer id) {
		try {
			Gradebook secondarygradebook = getSecondaryGradebook(id);
			if (secondarygradebook != null) {
				URI url = new URI(HTTP + "://" + LOCALHOST + secondaryServer + "/secondary/" + id);
				MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
				headers.add("userType", "admin");
				HttpEntity<?> request = new HttpEntity<Object>(headers);
				this.restTemplate.exchange(url, HttpMethod.DELETE, request, String.class);
			}
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
	}

	@RequestMapping(path = "/gradebook/{id}", method = RequestMethod.GET)
	public ResponseEntity<Gradebook> getGradebook(@PathVariable Integer id) {
		Gradebook gradebook = null;
		gradebook = primaryGradebookMap.get(id);
		return new ResponseEntity<Gradebook>(gradebook, HttpStatus.OK);
	}

	@RequestMapping(path = "/secondary/{id}", method = RequestMethod.GET)
	public ResponseEntity<Gradebook> getGradebookSecondaryServer(@PathVariable Integer id) {
		Gradebook gradebook = null;
		gradebook = secondaryGradebookMap.get(id);
		return new ResponseEntity<Gradebook>(gradebook, HttpStatus.OK);
	}

	public Gradebook getSecondaryGradebook(Integer id) {
		String uri = HTTP + "://" + LOCALHOST + secondaryServer + "/secondary/" + id;
		Gradebook gradebook;
		gradebook = this.restTemplate.getForObject(uri, Gradebook.class);
		return gradebook;
	}

	public boolean isValidGrade(String letter) {
		if (letter.equalsIgnoreCase("A") || letter.equalsIgnoreCase("B") || letter.equalsIgnoreCase("C")
				|| letter.equalsIgnoreCase("D") || letter.equalsIgnoreCase("E") || letter.equalsIgnoreCase("F")
				|| letter.equalsIgnoreCase("I") || letter.equalsIgnoreCase("W") || letter.equalsIgnoreCase("Z")
				|| letter.equalsIgnoreCase("A+") || letter.equalsIgnoreCase("A-") || letter.equalsIgnoreCase("B+")
				|| letter.equalsIgnoreCase("B-") || letter.equalsIgnoreCase("C+") || letter.equalsIgnoreCase("C-")
				|| letter.equalsIgnoreCase("D+") || letter.equalsIgnoreCase("D-"))
			return true;
		else
			return false;
	}

	public boolean containsGradeBookTitle(final Map<Integer, Gradebook> gradebookMap, final String title) {
		for (Integer id : gradebookMap.keySet()) {
			Gradebook gb = gradebookMap.get(id);
			if (gb.getTitle().equalsIgnoreCase(title)) {
				return true;
			}
		}
		return false;
	}

	public boolean containsGradeBookTitle(final GradebookList gradebooks, final String title) {
		if (gradebooks != null)
			for (Gradebook gb : gradebooks.gradebookList) {
				if (gb.getTitle().equalsIgnoreCase(title)) {
					return true;
				}
			}

		return false;
	}

	public boolean isValidTitle(String title) {
		if (null != title && !Character.isWhitespace(title.charAt(0)))
			return true;
		else
			return false;
	}
}