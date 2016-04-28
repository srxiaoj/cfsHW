/*
 * The MIT License
 *
 *  Copyright (c) 2015, Delta Star Team
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package com.deltastar.task7.core.repository.api;

import com.deltastar.task7.core.repository.domain.Position;

import java.util.List;

/**
 * Interface for todo repository.
 * <p>
 * Delta Star Team
 */
public interface PositionRepository {

    /**
     * Get todo by id.
     *
     * @param id the todo's id
     * @return the todo having the given id or null if no todo found with the given id
     */
    Position getPositionById(final int id);

    /**
     * Get todo list for the given user.
     *
     * @param customerId the user's id
     * @return the todo list for the given user
     */
    List<Position> getPositionListByCustomerId(final int customerId);

    Position getPossessedPositionByCustomerIdAndFundId(final int customerId, final int fundId);

    List<Position> getPossessedPositionByCustomerIdAndFundIdAndStatus(final int customerId, final int fundId);


    /**
     * Create a new todo.
     *
     * @param position the todo to create
     * @return the created todo
     */
    Position create(final Position position);

    /**
     * Update a todo.
     *
     * @param position the todo to update
     * @return the updated todo
     */
    Position update(Position position);

    /**
     * Remove a todo.
     *
     * @param position the todo to remove
     */
    void remove(final Position position);

}
