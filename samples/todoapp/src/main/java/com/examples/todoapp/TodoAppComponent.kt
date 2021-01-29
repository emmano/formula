package com.examples.todoapp

import com.examples.todoapp.data.TaskRepo
import com.examples.todoapp.tasks.TaskListFormula

class TodoAppComponent {
    private val repo: TaskRepo = TaskRepo()

    fun createTaskListFormula(): TaskListFormula {
        return TaskListFormula(repo)
    }
}
