package org.example.tasksapp;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskRepository repository;
    private final RabbitTemplate rabbitTemplate;

    public TaskController(TaskRepository repository, RabbitTemplate rabbitTemplate) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
    }

    // GET /tasks
    @GetMapping
    public List<Task> getAllTasks() {
        System.out.println("Showing tasks ....");
        return repository.findAll();
    }

    // POST /tasks
//    @PostMapping
//    public Task createTask(@RequestBody Task task) {
//        return repository.save(task);
//    }

    @PostMapping
    public Task createTask(@RequestBody Task task) {
        Task savedTask = repository.save(task);
        System.out.println("Createds New Task: " + savedTask);
        rabbitTemplate.convertAndSend("task-queue", "New task created: " + savedTask.getName());
        return savedTask;
    }

    // GET /tasks/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Task> getTaskById(@PathVariable int id) {
        Optional<Task> task = repository.findById(id);
        return task.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    // PUT /tasks/{id}
    @PutMapping("/{id}")
    public ResponseEntity<Task> updateTask(@PathVariable int id, @RequestBody Task updatedTask) {
        return repository.findById(id).map(task -> {
            task.setName(updatedTask.getName());
            task.setPriority(updatedTask.getPriority());
            return ResponseEntity.ok(repository.save(task));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    // DELETE /tasks/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable int id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
